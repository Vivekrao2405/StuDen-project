package com.studen.placement;

import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.PracticalAssessment;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptRepository;
import com.studen.practical.PracticalAttemptService;
import com.studen.practical.PracticalAttemptStatus;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionOption;
import com.studen.questionbank.QuestionOptionRepository;
import com.studen.questionbank.QuestionType;
import com.studen.resource.Resource;
import com.studen.resource.ResourceProgressStatus;
import com.studen.resource.StudentResourceProgressRepository;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student-facing Placement Prep (Phase 5): browsing PUBLISHED {@link PlacementSeries}, opening a
 * series, working through its modules/items, and tracking progress. Every write here only ever
 * touches {@link StudentSeriesProgress}/{@link StudentModuleItemProgress} (owned by this phase) or
 * delegates to an existing engine ({@code PracticalAttemptService} for coding items, the resource
 * package's own start/complete for reading items) — no second assessment or resource system.
 *
 * <p>Percentages and statuses are never stored redundantly: every read recomputes them from the
 * actual progress rows, the same "never trust a stored summary" posture
 * {@code PlacementAttemptService}/{@code PlacementReadinessService} already use for readiness
 * scoring. A QUESTION item is the one piece of content this phase tracks completion for directly
 * ({@link StudentModuleItemProgress}) — there is no other "answer one standalone question" surface
 * in StuDen to reuse. A PRACTICAL_ASSESSMENT item's completion is derived live from the student's
 * own {@link PracticalAttempt}; a RESOURCE item's completion is derived live from the student's own
 * {@code StudentResourceProgress} — both read straight from their existing systems of record.
 */
@Service
public class PlacementPrepService {

    private static final int MAX_PAGE_SIZE = 50;

    // Mirrors PlacementAttemptService.PRACTICAL_TERMINAL_STATUSES exactly — the same definition of
    // "the student's part is done" used for the readiness engine's practical slots.
    private static final Set<PracticalAttemptStatus> PRACTICAL_TERMINAL_STATUSES =
            Set.of(PracticalAttemptStatus.SUBMITTED, PracticalAttemptStatus.EVALUATED, PracticalAttemptStatus.EXPIRED);

    private final PlacementSeriesRepository seriesRepository;
    private final PlacementModuleRepository moduleRepository;
    private final PlacementModuleItemRepository moduleItemRepository;
    private final StudentSeriesProgressRepository seriesProgressRepository;
    private final StudentModuleItemProgressRepository itemProgressRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final PracticalAttemptRepository practicalAttemptRepository;
    private final PracticalAttemptService practicalAttemptService;
    private final StudentResourceProgressRepository studentResourceProgressRepository;
    private final PlacementSkillScoreRepository skillScoreRepository;
    private final PlacementScoringProperties scoringProperties;
    private final UserRepository userRepository;

    public PlacementPrepService(PlacementSeriesRepository seriesRepository, PlacementModuleRepository moduleRepository,
            PlacementModuleItemRepository moduleItemRepository, StudentSeriesProgressRepository seriesProgressRepository,
            StudentModuleItemProgressRepository itemProgressRepository, QuestionOptionRepository questionOptionRepository,
            PracticalAttemptRepository practicalAttemptRepository, PracticalAttemptService practicalAttemptService,
            StudentResourceProgressRepository studentResourceProgressRepository,
            PlacementSkillScoreRepository skillScoreRepository, PlacementScoringProperties scoringProperties,
            UserRepository userRepository) {
        this.seriesRepository = seriesRepository;
        this.moduleRepository = moduleRepository;
        this.moduleItemRepository = moduleItemRepository;
        this.seriesProgressRepository = seriesProgressRepository;
        this.itemProgressRepository = itemProgressRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.practicalAttemptRepository = practicalAttemptRepository;
        this.practicalAttemptService = practicalAttemptService;
        this.studentResourceProgressRepository = studentResourceProgressRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.scoringProperties = scoringProperties;
        this.userRepository = userRepository;
    }

    // --- Catalog -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlacementPageResponse<StudentPlacementSeriesResponse> listSeries(UUID studentId, UUID roleId,
            UUID companyId, CompanyType companyType, PreparationType preparationType, Difficulty difficulty,
            UUID skillId, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<PlacementSeries> result = seriesRepository.searchPublished(roleId, companyId, companyType,
                preparationType, difficulty, skillId, normalizedSearch, pageable);

        Rollup rollup = loadRollup(studentId, result.getContent());
        return PlacementPageResponse.of(result.map(series -> buildSeriesResponse(series, rollup)));
    }

    @Transactional(readOnly = true)
    public StudentPlacementSeriesDetailResponse getSeriesDetail(UUID studentId, UUID seriesId) {
        PlacementSeries series = requirePublishedSeries(seriesId);
        Rollup rollup = loadRollup(studentId, List.of(series));
        return buildSeriesDetailResponse(series, rollup, loadLatestScores(studentId));
    }

    @Transactional
    public StudentPlacementSeriesDetailResponse startSeries(UUID studentId, UUID seriesId) {
        PlacementSeries series = requirePublishedSeries(seriesId);
        ensureSeriesStarted(studentId, series);
        Rollup rollup = loadRollup(studentId, List.of(series));
        return buildSeriesDetailResponse(series, rollup, loadLatestScores(studentId));
    }

    @Transactional(readOnly = true)
    public PlacementContinueResponse getContinuePointer(UUID studentId, UUID seriesId) {
        PlacementSeries series = requirePublishedSeries(seriesId);
        Rollup rollup = loadRollup(studentId, List.of(series));
        for (PlacementModule module : rollup.modulesBySeries.getOrDefault(seriesId, List.of())) {
            for (PlacementModuleItem item : rollup.itemsByModule.getOrDefault(module.getId(), List.of())) {
                if (rollup.statusOf(item) != PlacementProgressStatus.COMPLETED) {
                    return PlacementContinueResponse.of(seriesId, module, PlacementModuleItemResponse.from(item));
                }
            }
        }
        return PlacementContinueResponse.complete(seriesId);
    }

    // --- Module items --------------------------------------------------------------------------

    @Transactional
    public PlacementModuleItemDetailResponse getModuleItemDetail(UUID studentId, UUID itemId) {
        PlacementModuleItem item = findItem(itemId);
        requirePublished(item.getModule().getSeries());
        ensureSeriesStarted(studentId, item.getModule().getSeries());
        return buildItemDetail(studentId, item);
    }

    @Transactional
    public PlacementModuleItemAnswerResponse answerQuestionItem(UUID studentId, UUID itemId,
            List<UUID> selectedOptionIds) {
        PlacementModuleItem item = findItem(itemId);
        requirePublished(item.getModule().getSeries());
        if (item.getItemType() != ModuleItemType.QUESTION) {
            throw new InvalidRequestException("This item isn't a question.");
        }
        Question question = item.getQuestion();
        List<QuestionOption> options = questionOptionRepository.findAllByQuestionIdOrderByDisplayOrderAsc(question.getId());
        Set<UUID> validOptionIds = options.stream().map(QuestionOption::getId).collect(Collectors.toSet());

        Set<UUID> selected = new LinkedHashSet<>(selectedOptionIds);
        if (selected.isEmpty()) {
            throw new InvalidRequestException("At least one option must be selected.");
        }
        if (selected.size() != selectedOptionIds.size()) {
            throw new InvalidRequestException("Duplicate option selected.");
        }
        if (!validOptionIds.containsAll(selected)) {
            throw new InvalidRequestException("One or more selected options are invalid for this question.");
        }
        QuestionType type = question.getQuestionType();
        if ((type == QuestionType.MCQ_SINGLE || type == QuestionType.TRUE_FALSE) && selected.size() != 1) {
            throw new InvalidRequestException("This question requires exactly one selected option.");
        }

        Set<UUID> correctOptionIds = options.stream().filter(QuestionOption::isCorrect).map(QuestionOption::getId)
                .collect(Collectors.toSet());
        boolean correct = selected.equals(correctOptionIds);

        User student = userRepository.getReferenceById(studentId);
        StudentModuleItemProgress progress = itemProgressRepository.findByStudentIdAndModuleItemId(studentId, itemId)
                .orElseGet(() -> new StudentModuleItemProgress(student, item, PlacementProgressStatus.NOT_STARTED));
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(Instant.now());
        }
        progress.setStatus(PlacementProgressStatus.COMPLETED);
        progress.setCompletedAt(Instant.now());
        itemProgressRepository.save(progress);

        ensureSeriesStarted(studentId, item.getModule().getSeries());
        syncSeriesCompletionIfNeeded(studentId, item.getModule().getSeries());

        return new PlacementModuleItemAnswerResponse(itemId, correct, List.copyOf(correctOptionIds),
                question.getExplanation(), PlacementProgressStatus.COMPLETED);
    }

    @Transactional
    public PlacementModuleItemDetailResponse startPracticalItem(UUID studentId, UUID itemId) {
        PlacementModuleItem item = findItem(itemId);
        requirePublished(item.getModule().getSeries());
        if (item.getItemType() != ModuleItemType.PRACTICAL_ASSESSMENT) {
            throw new InvalidRequestException("This item isn't a practical assessment.");
        }
        practicalAttemptService.startOrResume(studentId, item.getPracticalAssessment().getId());
        ensureSeriesStarted(studentId, item.getModule().getSeries());
        return buildItemDetail(studentId, item);
    }

    // --- Internals -----------------------------------------------------------------------------

    private void ensureSeriesStarted(UUID studentId, PlacementSeries series) {
        if (seriesProgressRepository.findByStudentIdAndSeriesId(studentId, series.getId()).isPresent()) {
            return;
        }
        User student = userRepository.getReferenceById(studentId);
        StudentSeriesProgress progress = new StudentSeriesProgress(student, series, PlacementProgressStatus.IN_PROGRESS);
        progress.setStartedAt(Instant.now());
        seriesProgressRepository.save(progress);
    }

    // Persists the series roll-up's COMPLETED transition once every module is done — a cheap,
    // rare write (only fires the moment the last item is finished), never read from afterward.
    private void syncSeriesCompletionIfNeeded(UUID studentId, PlacementSeries series) {
        Rollup rollup = loadRollup(studentId, List.of(series));
        List<StudentPlacementModuleResponse> modules = rollup.modulesBySeries.getOrDefault(series.getId(), List.of())
                .stream().map(m -> buildModuleResponse(m, rollup)).toList();
        if (seriesStatus(modules) != PlacementProgressStatus.COMPLETED) {
            return;
        }
        seriesProgressRepository.findByStudentIdAndSeriesId(studentId, series.getId()).ifPresent(progress -> {
            if (progress.getStatus() != PlacementProgressStatus.COMPLETED) {
                progress.setStatus(PlacementProgressStatus.COMPLETED);
                progress.setCompletedAt(Instant.now());
                seriesProgressRepository.save(progress);
            }
        });
    }

    private PlacementModuleItemDetailResponse buildItemDetail(UUID studentId, PlacementModuleItem item) {
        UUID moduleId = item.getModule().getId();
        UUID seriesId = item.getModule().getSeries().getId();
        switch (item.getItemType()) {
            case QUESTION -> {
                Question question = item.getQuestion();
                List<QuestionOption> options =
                        questionOptionRepository.findAllByQuestionIdOrderByDisplayOrderAsc(question.getId());
                PlacementProgressStatus status = itemProgressRepository
                        .findByStudentIdAndModuleItemId(studentId, item.getId())
                        .map(StudentModuleItemProgress::getStatus).orElse(PlacementProgressStatus.NOT_STARTED);
                boolean revealed = status == PlacementProgressStatus.COMPLETED;
                List<UUID> correctIds = revealed
                        ? options.stream().filter(QuestionOption::isCorrect).map(QuestionOption::getId).toList()
                        : null;
                return new PlacementModuleItemDetailResponse(item.getId(), moduleId, seriesId, item.getItemType(),
                        item.isRequired(), status, question.getSkill().getName(), question.getQuestionText(),
                        question.getQuestionType(), question.getDifficulty(),
                        options.stream().map(PlacementAttemptOptionView::from).toList(), correctIds,
                        revealed ? question.getExplanation() : null, null, null, null, null, null, null, null, null);
            }
            case PRACTICAL_ASSESSMENT -> {
                PracticalAssessment practical = item.getPracticalAssessment();
                List<PracticalAttempt> attempts = practicalAttemptRepository
                        .findAllByUserIdAndPracticalAssessmentIdInOrderByStartedAtDesc(studentId, List.of(practical.getId()));
                PracticalAttempt latest = attempts.isEmpty() ? null : attempts.get(0);
                PlacementProgressStatus status = latest == null ? PlacementProgressStatus.NOT_STARTED
                        : (PRACTICAL_TERMINAL_STATUSES.contains(latest.getStatus()) ? PlacementProgressStatus.COMPLETED
                                : PlacementProgressStatus.IN_PROGRESS);
                return new PlacementModuleItemDetailResponse(item.getId(), moduleId, seriesId, item.getItemType(),
                        item.isRequired(), status, practical.getSkill().getName(), null, null, null, List.of(), null,
                        null, practical.getId(), practical.getTitle(), practical.getPracticalType(),
                        latest == null ? null : latest.getId(), latest == null ? null : latest.getStatus(), null,
                        null, null);
            }
            case RESOURCE -> {
                Resource resource = item.getResource();
                PlacementProgressStatus status = studentResourceProgressRepository
                        .findByStudentIdAndResourceId(studentId, resource.getId())
                        .map(p -> toPlacementStatus(p.getStatus())).orElse(PlacementProgressStatus.NOT_STARTED);
                return new PlacementModuleItemDetailResponse(item.getId(), moduleId, seriesId, item.getItemType(),
                        item.isRequired(), status, resource.getSkill().getName(), null, null, null, List.of(), null,
                        null, null, null, null, null, null, resource.getId(), resource.getTitle(),
                        resource.getResourceType());
            }
            default -> throw new IllegalStateException("Unsupported item type: " + item.getItemType());
        }
    }

    private StudentPlacementSeriesResponse buildSeriesResponse(PlacementSeries series, Rollup rollup) {
        List<StudentPlacementModuleResponse> modules = rollup.modulesBySeries.getOrDefault(series.getId(), List.of())
                .stream().map(m -> buildModuleResponse(m, rollup)).toList();
        return new StudentPlacementSeriesResponse(series.getId(), series.getName(), series.getDescription(),
                series.getCompany() == null ? null : series.getCompany().getId(),
                series.getCompany() == null ? null : series.getCompany().getName(), series.getTargetRole().getId(),
                series.getTargetRole().getName(), series.getCompanyType(), series.getPreparationType(),
                series.getDifficulty(), series.getEstimatedDurationHours(), series.getThumbnailUrl(),
                series.getSkillsCovered().stream().map(SkillResponse::from).toList(), modules.size(),
                seriesPercentage(modules), seriesStatus(modules), series.getUpdatedAt());
    }

    private StudentPlacementSeriesDetailResponse buildSeriesDetailResponse(PlacementSeries series, Rollup rollup,
            Map<UUID, Integer> latestScoreBySkill) {
        List<StudentPlacementModuleResponse> modules = rollup.modulesBySeries.getOrDefault(series.getId(), List.of())
                .stream().map(m -> buildModuleResponse(m, rollup)).toList();
        List<PlacementSkillCoverageView> skills = series.getSkillsCovered().stream()
                .map(skill -> PlacementSkillCoverageView.from(skill, latestScoreBySkill.get(skill.getId()), scoringProperties))
                .toList();
        return new StudentPlacementSeriesDetailResponse(series.getId(), series.getName(), series.getDescription(),
                series.getCompany() == null ? null : series.getCompany().getId(),
                series.getCompany() == null ? null : series.getCompany().getName(), series.getTargetRole().getId(),
                series.getTargetRole().getName(), series.getCompanyType(), series.getPreparationType(),
                series.getDifficulty(), series.getEstimatedDurationHours(), series.getThumbnailUrl(), skills,
                seriesPercentage(modules), seriesStatus(modules), modules, series.getUpdatedAt());
    }

    private StudentPlacementModuleResponse buildModuleResponse(PlacementModule module, Rollup rollup) {
        List<PlacementModuleItem> items = rollup.itemsByModule.getOrDefault(module.getId(), List.of());
        List<StudentPlacementModuleItemResponse> itemResponses = items.stream()
                .map(item -> StudentPlacementModuleItemResponse.from(item, rollup.statusOf(item))).toList();

        List<PlacementModuleItem> requiredItems = items.stream().filter(PlacementModuleItem::isRequired).toList();
        int totalRequired = requiredItems.size();
        long completedRequired =
                requiredItems.stream().filter(item -> rollup.statusOf(item) == PlacementProgressStatus.COMPLETED).count();
        int target = module.getRequiredItemCount() != null ? module.getRequiredItemCount() : totalRequired;
        int percentage = target <= 0 ? 0 : (int) Math.min(100, Math.round(completedRequired * 100.0 / target));
        int completedTotal =
                (int) items.stream().filter(item -> rollup.statusOf(item) == PlacementProgressStatus.COMPLETED).count();

        PlacementProgressStatus status;
        if (target > 0 && completedRequired >= target) {
            status = PlacementProgressStatus.COMPLETED;
        } else if (items.stream().anyMatch(item -> rollup.statusOf(item) != PlacementProgressStatus.NOT_STARTED)) {
            status = PlacementProgressStatus.IN_PROGRESS;
        } else {
            status = PlacementProgressStatus.NOT_STARTED;
        }

        return new StudentPlacementModuleResponse(module.getId(), module.getName(), module.getDescription(),
                module.getDisplayOrder(), module.getRequiredItemCount(), items.size(), completedTotal, percentage,
                status, itemResponses);
    }

    // A series percentage averages only modules that actually have content — an empty module (an
    // admin still building it out) is neither "0% done" nor "100% done", so it doesn't count either
    // way rather than silently dragging the average down.
    private int seriesPercentage(List<StudentPlacementModuleResponse> modules) {
        List<StudentPlacementModuleResponse> withItems = modules.stream().filter(m -> m.totalItemCount() > 0).toList();
        if (withItems.isEmpty()) {
            return 0;
        }
        double average = withItems.stream().mapToInt(StudentPlacementModuleResponse::progressPercentage).average().orElse(0);
        return (int) Math.round(average);
    }

    private PlacementProgressStatus seriesStatus(List<StudentPlacementModuleResponse> modules) {
        List<StudentPlacementModuleResponse> withItems = modules.stream().filter(m -> m.totalItemCount() > 0).toList();
        if (withItems.isEmpty()) {
            return PlacementProgressStatus.NOT_STARTED;
        }
        if (withItems.stream().allMatch(m -> m.status() == PlacementProgressStatus.COMPLETED)) {
            return PlacementProgressStatus.COMPLETED;
        }
        if (withItems.stream().anyMatch(m -> m.status() != PlacementProgressStatus.NOT_STARTED)) {
            return PlacementProgressStatus.IN_PROGRESS;
        }
        return PlacementProgressStatus.NOT_STARTED;
    }

    private PlacementProgressStatus toPlacementStatus(ResourceProgressStatus status) {
        return switch (status) {
            case NOT_STARTED -> PlacementProgressStatus.NOT_STARTED;
            case IN_PROGRESS -> PlacementProgressStatus.IN_PROGRESS;
            case COMPLETED -> PlacementProgressStatus.COMPLETED;
        };
    }

    private Map<UUID, Integer> loadLatestScores(UUID studentId) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (PlacementSkillScore score : skillScoreRepository.findByStudentIdOrderByRecordedAtDesc(studentId)) {
            result.putIfAbsent(score.getSkill().getId(), score.getScorePercentage());
        }
        return result;
    }

    /**
     * Batches every query the roll-up needs across an entire set of series (a catalog page, or a
     * single series) in one shot: modules per series, items per module, and each item's progress
     * from its own system of record (question progress, practical attempts, resource progress).
     */
    private Rollup loadRollup(UUID studentId, List<PlacementSeries> seriesList) {
        List<UUID> seriesIds = seriesList.stream().map(PlacementSeries::getId).toList();
        Map<UUID, List<PlacementModule>> modulesBySeries = new LinkedHashMap<>();
        for (UUID id : seriesIds) {
            modulesBySeries.put(id, new ArrayList<>());
        }
        List<PlacementModule> modules =
                seriesIds.isEmpty() ? List.of() : moduleRepository.findBySeriesIdInOrderByDisplayOrderAsc(seriesIds);
        for (PlacementModule module : modules) {
            modulesBySeries.get(module.getSeries().getId()).add(module);
        }

        List<UUID> moduleIds = modules.stream().map(PlacementModule::getId).toList();
        Map<UUID, List<PlacementModuleItem>> itemsByModule = new LinkedHashMap<>();
        for (UUID id : moduleIds) {
            itemsByModule.put(id, new ArrayList<>());
        }
        List<PlacementModuleItem> items =
                moduleIds.isEmpty() ? List.of() : moduleItemRepository.findByModuleIdInOrderByDisplayOrderAsc(moduleIds);
        for (PlacementModuleItem item : items) {
            itemsByModule.get(item.getModule().getId()).add(item);
        }

        List<UUID> questionItemIds =
                items.stream().filter(i -> i.getItemType() == ModuleItemType.QUESTION).map(PlacementModuleItem::getId).toList();
        List<UUID> practicalAssessmentIds = items.stream().filter(i -> i.getItemType() == ModuleItemType.PRACTICAL_ASSESSMENT)
                .map(i -> i.getPracticalAssessment().getId()).distinct().toList();
        List<UUID> resourceIds = items.stream().filter(i -> i.getItemType() == ModuleItemType.RESOURCE)
                .map(i -> i.getResource().getId()).distinct().toList();

        Map<UUID, PlacementProgressStatus> questionStatusByItemId = questionItemIds.isEmpty() ? Map.of()
                : itemProgressRepository.findAllByStudentIdAndModuleItemIdIn(studentId, questionItemIds).stream()
                        .collect(Collectors.toMap(p -> p.getModuleItem().getId(), StudentModuleItemProgress::getStatus));

        // Ordered newest-first by the repository query, so the first entry kept per key is the
        // student's latest attempt for that practical assessment.
        Map<UUID, PracticalAttemptStatus> practicalStatusByAssessmentId = practicalAssessmentIds.isEmpty() ? Map.of()
                : practicalAttemptRepository
                        .findAllByUserIdAndPracticalAssessmentIdInOrderByStartedAtDesc(studentId, practicalAssessmentIds)
                        .stream()
                        .collect(Collectors.toMap(a -> a.getPracticalAssessment().getId(), PracticalAttempt::getStatus,
                                (first, second) -> first));

        Map<UUID, ResourceProgressStatus> resourceStatusByResourceId = resourceIds.isEmpty() ? Map.of()
                : studentResourceProgressRepository.findAllByStudentIdAndResourceIdIn(studentId, resourceIds).stream()
                        .collect(Collectors.toMap(p -> p.getResource().getId(), p -> p.getStatus()));

        Map<UUID, PlacementProgressStatus> itemStatus = new LinkedHashMap<>();
        for (PlacementModuleItem item : items) {
            itemStatus.put(item.getId(), resolveItemStatus(item, questionStatusByItemId, practicalStatusByAssessmentId,
                    resourceStatusByResourceId));
        }

        return new Rollup(modulesBySeries, itemsByModule, itemStatus);
    }

    private PlacementProgressStatus resolveItemStatus(PlacementModuleItem item,
            Map<UUID, PlacementProgressStatus> questionStatusByItemId,
            Map<UUID, PracticalAttemptStatus> practicalStatusByAssessmentId,
            Map<UUID, ResourceProgressStatus> resourceStatusByResourceId) {
        return switch (item.getItemType()) {
            case QUESTION -> questionStatusByItemId.getOrDefault(item.getId(), PlacementProgressStatus.NOT_STARTED);
            case PRACTICAL_ASSESSMENT -> {
                PracticalAttemptStatus status = practicalStatusByAssessmentId.get(item.getPracticalAssessment().getId());
                if (status == null) {
                    yield PlacementProgressStatus.NOT_STARTED;
                }
                yield PRACTICAL_TERMINAL_STATUSES.contains(status) ? PlacementProgressStatus.COMPLETED
                        : PlacementProgressStatus.IN_PROGRESS;
            }
            case RESOURCE -> {
                ResourceProgressStatus status = resourceStatusByResourceId.get(item.getResource().getId());
                yield status == null ? PlacementProgressStatus.NOT_STARTED : toPlacementStatus(status);
            }
        };
    }

    private PlacementSeries requirePublishedSeries(UUID id) {
        PlacementSeries series =
                seriesRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Placement series not found"));
        requirePublished(series);
        return series;
    }

    // Draft/archived series 404 for students exactly like a nonexistent one (spec §23/§29/§30) —
    // never a distinct "not visible yet" response that would leak that the id exists.
    private void requirePublished(PlacementSeries series) {
        if (series.getStatus() != PlacementContentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Placement series not found");
        }
    }

    private PlacementModuleItem findItem(UUID itemId) {
        return moduleItemRepository.findById(itemId).orElseThrow(() -> new ResourceNotFoundException("Module item not found"));
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private record Rollup(Map<UUID, List<PlacementModule>> modulesBySeries, Map<UUID, List<PlacementModuleItem>> itemsByModule,
            Map<UUID, PlacementProgressStatus> itemStatus) {
        PlacementProgressStatus statusOf(PlacementModuleItem item) {
            return itemStatus.getOrDefault(item.getId(), PlacementProgressStatus.NOT_STARTED);
        }
    }
}
