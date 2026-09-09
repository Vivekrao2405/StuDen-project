package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.PracticalAssessment;
import com.studen.practical.PracticalAssessmentRepository;
import com.studen.practical.PracticalAssessmentStatus;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionStatus;
import com.studen.resource.Resource;
import com.studen.resource.ResourceRepository;
import com.studen.resource.ResourceStatus;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin authoring of Placement Prep content: series, their modules, and the items inside each
 * module. Phase 0 builds the structure only — there is no student-facing prep workflow here.
 *
 * <p>A module item points at content that already exists elsewhere in StuDen (a Question Bank
 * question, a practical/coding assessment, or a learning resource) and only ever links to it. That
 * is what keeps placement from growing a second copy of the question, coding or resource systems.
 *
 * <p>Deletion posture matches {@code AdminResourceService}: anything a student has already made
 * progress on is archived, never deleted, so progress is never destroyed as a side effect.
 */
@Service
public class PlacementSeriesService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlacementSeriesRepository seriesRepository;
    private final PlacementModuleRepository moduleRepository;
    private final PlacementModuleItemRepository moduleItemRepository;
    private final PlacementRoleRepository roleRepository;
    private final PlacementCompanyRepository companyRepository;
    private final SkillRepository skillRepository;
    private final QuestionRepository questionRepository;
    private final PracticalAssessmentRepository practicalAssessmentRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final StudentSeriesProgressRepository seriesProgressRepository;
    private final StudentModuleItemProgressRepository itemProgressRepository;

    public PlacementSeriesService(PlacementSeriesRepository seriesRepository,
            PlacementModuleRepository moduleRepository, PlacementModuleItemRepository moduleItemRepository,
            PlacementRoleRepository roleRepository, PlacementCompanyRepository companyRepository,
            SkillRepository skillRepository, QuestionRepository questionRepository,
            PracticalAssessmentRepository practicalAssessmentRepository, ResourceRepository resourceRepository,
            UserRepository userRepository, StudentSeriesProgressRepository seriesProgressRepository,
            StudentModuleItemProgressRepository itemProgressRepository) {
        this.seriesRepository = seriesRepository;
        this.moduleRepository = moduleRepository;
        this.moduleItemRepository = moduleItemRepository;
        this.roleRepository = roleRepository;
        this.companyRepository = companyRepository;
        this.skillRepository = skillRepository;
        this.questionRepository = questionRepository;
        this.practicalAssessmentRepository = practicalAssessmentRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.seriesProgressRepository = seriesProgressRepository;
        this.itemProgressRepository = itemProgressRepository;
    }

    // --- Series ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlacementPageResponse<PlacementSeriesResponse> listSeries(UUID roleId, UUID companyId,
            CompanyType companyType, PreparationType preparationType, PlacementContentStatus status, String search,
            int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<PlacementSeries> result = seriesRepository.search(roleId, companyId, companyType, preparationType,
                status, normalizedSearch, pageable);

        List<UUID> ids = result.getContent().stream().map(PlacementSeries::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (IdCountView view : seriesRepository.countModulesBySeries(ids)) {
                counts.put(view.id(), view.count());
            }
        }
        return PlacementPageResponse.of(result.map(series ->
                PlacementSeriesResponse.from(series, counts.getOrDefault(series.getId(), 0L).intValue())));
    }

    @Transactional(readOnly = true)
    public PlacementSeriesDetailResponse getSeries(UUID id) {
        PlacementSeries series = findSeries(id);
        return PlacementSeriesDetailResponse.from(series, loadModuleResponses(id));
    }

    @Transactional
    public PlacementSeriesDetailResponse createSeries(UUID adminUserId, PlacementSeriesRequest request) {
        PlacementRole role = findRole(request.targetRoleId());
        User createdBy = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PlacementSeries series =
                new PlacementSeries(request.name().trim(), role, request.difficulty(), createdBy);
        applySeriesRequest(series, request, role);
        return PlacementSeriesDetailResponse.from(seriesRepository.save(series), List.of());
    }

    @Transactional
    public PlacementSeriesDetailResponse updateSeries(UUID id, PlacementSeriesRequest request) {
        PlacementSeries series = findSeries(id);
        applySeriesRequest(series, request, findRole(request.targetRoleId()));
        return PlacementSeriesDetailResponse.from(series, loadModuleResponses(id));
    }

    @Transactional
    public void deleteSeries(UUID id) {
        PlacementSeries series = findSeries(id);
        if (seriesProgressRepository.existsBySeriesId(id) || itemProgressRepository.existsBySeriesId(id)) {
            throw new ConflictException("Students have already started this series — archive it instead");
        }
        seriesRepository.delete(series);
    }

    @Transactional
    public PlacementSeriesDetailResponse setSeriesStatus(UUID id, PlacementContentStatus status) {
        PlacementSeries series = findSeries(id);
        List<PlacementModule> modules = moduleRepository.findBySeriesIdOrderByDisplayOrderAsc(id);
        if (status == PlacementContentStatus.PUBLISHED && modules.isEmpty()) {
            throw new InvalidRequestException("Add at least one module before publishing this series");
        }
        series.setStatus(status);
        return PlacementSeriesDetailResponse.from(series, loadModuleResponses(id));
    }

    // --- Modules ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlacementModuleResponse> listModules(UUID seriesId) {
        requireSeriesExists(seriesId);
        return loadModuleResponses(seriesId);
    }

    @Transactional
    public PlacementModuleResponse createModule(UUID seriesId, PlacementModuleRequest request) {
        PlacementSeries series = findSeries(seriesId);
        int displayOrder = request.displayOrder() != null
                ? request.displayOrder()
                : moduleRepository.findBySeriesIdOrderByDisplayOrderAsc(seriesId).size();

        PlacementModule module = new PlacementModule(series, request.name().trim(), displayOrder);
        module.setDescription(trimToNull(request.description()));
        module.setRequiredItemCount(request.requiredItemCount());

        // Added through the owning collection, not saved standalone: the series owns its modules
        // (cascade + orphanRemoval), so leaving the parent side stale would let a later
        // seriesRepository.delete cascade past a module Hibernate still holds as managed.
        series.getModules().add(module);
        seriesRepository.flush();
        return PlacementModuleResponse.from(module, List.of());
    }

    @Transactional
    public PlacementModuleResponse updateModule(UUID moduleId, PlacementModuleRequest request) {
        PlacementModule module = findModule(moduleId);
        module.setName(request.name().trim());
        module.setDescription(trimToNull(request.description()));
        if (request.displayOrder() != null) {
            module.setDisplayOrder(request.displayOrder());
        }
        module.setRequiredItemCount(request.requiredItemCount());
        return PlacementModuleResponse.from(module, moduleItemRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId));
    }

    @Transactional
    public void deleteModule(UUID moduleId) {
        PlacementModule module = findModule(moduleId);
        if (itemProgressRepository.existsByModuleId(moduleId)) {
            throw new ConflictException("Students have already started items in this module — remove their content instead");
        }
        // Removed through the owning collection so orphanRemoval deletes it and the parent side
        // does not keep a stale reference — the mirror of createModule above.
        module.getSeries().getModules().remove(module);
        moduleRepository.flush();
    }

    /**
     * Reorders every module in a series by list position. The request must list exactly the
     * modules that belong to this series, so a partial list can never leave the rest with stale
     * or colliding positions.
     */
    @Transactional
    public List<PlacementModuleResponse> reorderModules(UUID seriesId, ReorderRequest request) {
        requireSeriesExists(seriesId);
        List<PlacementModule> modules = moduleRepository.findBySeriesIdOrderByDisplayOrderAsc(seriesId);
        Map<UUID, PlacementModule> byId = new HashMap<>();
        modules.forEach(module -> byId.put(module.getId(), module));

        validateReorder(request.orderedIds(), byId.keySet(), "module");
        for (int index = 0; index < request.orderedIds().size(); index++) {
            byId.get(request.orderedIds().get(index)).setDisplayOrder(index);
        }
        moduleRepository.flush();
        return loadModuleResponses(seriesId);
    }

    // --- Module items ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlacementModuleItemResponse> listModuleItems(UUID moduleId) {
        findModule(moduleId);
        return moduleItemRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId).stream()
                .map(PlacementModuleItemResponse::from)
                .toList();
    }

    @Transactional
    public PlacementModuleItemResponse addModuleItem(UUID moduleId, PlacementModuleItemRequest request) {
        PlacementModule module = findModule(moduleId);
        int displayOrder = request.displayOrder() != null
                ? request.displayOrder()
                : moduleItemRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId).size();
        boolean required = request.required() == null || request.required();

        PlacementModuleItem item = new PlacementModuleItem(module, request.itemType(), displayOrder, required);
        attachTarget(item, request);
        // Same reason as createModule: the module owns its items, so both sides are kept in sync.
        module.getItems().add(item);
        moduleRepository.flush();
        return PlacementModuleItemResponse.from(item);
    }

    @Transactional
    public void removeModuleItem(UUID itemId) {
        PlacementModuleItem item = moduleItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Module item not found"));
        if (itemProgressRepository.existsByModuleItemId(itemId)) {
            throw new ConflictException("Students have already started this item — it cannot be removed");
        }
        item.getModule().getItems().remove(item);
        moduleItemRepository.flush();
    }

    @Transactional
    public List<PlacementModuleItemResponse> reorderModuleItems(UUID moduleId, ReorderRequest request) {
        findModule(moduleId);
        List<PlacementModuleItem> items = moduleItemRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId);
        Map<UUID, PlacementModuleItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));

        validateReorder(request.orderedIds(), byId.keySet(), "item");
        for (int index = 0; index < request.orderedIds().size(); index++) {
            byId.get(request.orderedIds().get(index)).setDisplayOrder(index);
        }
        moduleItemRepository.flush();
        return moduleItemRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId).stream()
                .map(PlacementModuleItemResponse::from)
                .toList();
    }

    // --- Internals -------------------------------------------------------------------------

    /**
     * Resolves the one content row this item points at, rejecting any request whose ids do not
     * match its declared type before the database CHECK constraint would. Only published content
     * is linkable, so a draft or archived question, practical assessment or resource can never
     * reach a student through a placement module.
     */
    private void attachTarget(PlacementModuleItem item, PlacementModuleItemRequest request) {
        switch (request.itemType()) {
            case QUESTION -> {
                requireOnlyTarget(request.questionId(), request.practicalAssessmentId(), request.resourceId(),
                        "questionId");
                Question question = questionRepository.findById(request.questionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
                if (question.getStatus() != QuestionStatus.PUBLISHED) {
                    throw new InvalidRequestException("Only published questions can be added to a module");
                }
                if (moduleItemRepository.existsByModuleIdAndQuestionId(item.getModule().getId(), question.getId())) {
                    throw new ConflictException("This question is already in this module");
                }
                item.setQuestion(question);
            }
            case PRACTICAL_ASSESSMENT -> {
                requireOnlyTarget(request.practicalAssessmentId(), request.questionId(), request.resourceId(),
                        "practicalAssessmentId");
                PracticalAssessment practical = practicalAssessmentRepository.findById(request.practicalAssessmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));
                if (practical.getStatus() != PracticalAssessmentStatus.PUBLISHED) {
                    throw new InvalidRequestException("Only published practical assessments can be added to a module");
                }
                if (moduleItemRepository.existsByModuleIdAndPracticalAssessmentId(item.getModule().getId(),
                        practical.getId())) {
                    throw new ConflictException("This practical assessment is already in this module");
                }
                item.setPracticalAssessment(practical);
            }
            case RESOURCE -> {
                requireOnlyTarget(request.resourceId(), request.questionId(), request.practicalAssessmentId(),
                        "resourceId");
                Resource resource = resourceRepository.findById(request.resourceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
                if (resource.getStatus() != ResourceStatus.PUBLISHED) {
                    throw new InvalidRequestException("Only published resources can be added to a module");
                }
                if (moduleItemRepository.existsByModuleIdAndResourceId(item.getModule().getId(), resource.getId())) {
                    throw new ConflictException("This resource is already in this module");
                }
                item.setResource(resource);
            }
            default -> throw new InvalidRequestException("Unsupported item type: " + request.itemType());
        }
    }

    private void requireOnlyTarget(UUID expected, UUID other1, UUID other2, String expectedField) {
        if (expected == null) {
            throw new InvalidRequestException(expectedField + " is required for this item type");
        }
        if (other1 != null || other2 != null) {
            throw new InvalidRequestException("Only " + expectedField + " may be set for this item type");
        }
    }

    private void validateReorder(List<UUID> orderedIds, Set<UUID> actualIds, String label) {
        Set<UUID> requested = new LinkedHashSet<>(orderedIds);
        if (requested.size() != orderedIds.size()) {
            throw new InvalidRequestException("The same " + label + " was listed more than once");
        }
        if (!requested.equals(actualIds)) {
            throw new InvalidRequestException("The reorder request must list every " + label + " exactly once");
        }
    }

    private void applySeriesRequest(PlacementSeries series, PlacementSeriesRequest request, PlacementRole role) {
        series.setName(request.name().trim());
        series.setDescription(trimToNull(request.description()));
        series.setTargetRole(role);
        series.setCompanyType(request.companyType());
        series.setPreparationType(request.preparationType());
        series.setDifficulty(request.difficulty());
        series.setEstimatedDurationHours(request.estimatedDurationHours());
        series.setThumbnailUrl(trimToNull(request.thumbnailUrl()));

        if (request.companyId() == null) {
            series.setCompany(null);
        } else {
            series.setCompany(companyRepository.findById(request.companyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found")));
        }

        series.getSkillsCovered().clear();
        series.getSkillsCovered().addAll(loadSkills(request.skillIds()));
    }

    private Set<Skill> loadSkills(List<UUID> skillIds) {
        Set<Skill> result = new LinkedHashSet<>();
        if (skillIds == null || skillIds.isEmpty()) {
            return result;
        }
        Map<UUID, Skill> found = new HashMap<>();
        for (Skill skill : skillRepository.findAllByIdIn(new LinkedHashSet<>(skillIds))) {
            found.put(skill.getId(), skill);
        }
        for (UUID id : skillIds) {
            Skill skill = found.get(id);
            if (skill == null) {
                throw new ResourceNotFoundException("Skill not found: " + id);
            }
            result.add(skill);
        }
        return result;
    }

    private List<PlacementModuleResponse> loadModuleResponses(UUID seriesId) {
        List<PlacementModule> modules = moduleRepository.findBySeriesIdOrderByDisplayOrderAsc(seriesId);
        List<PlacementModuleResponse> responses = new ArrayList<>(modules.size());
        for (PlacementModule module : modules) {
            responses.add(PlacementModuleResponse.from(module,
                    moduleItemRepository.findByModuleIdOrderByDisplayOrderAsc(module.getId())));
        }
        return responses;
    }

    private PlacementSeries findSeries(UUID id) {
        return seriesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Placement series not found"));
    }

    private void requireSeriesExists(UUID id) {
        if (!seriesRepository.existsById(id)) {
            throw new ResourceNotFoundException("Placement series not found");
        }
    }

    private PlacementModule findModule(UUID id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
    }

    private PlacementRole findRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
