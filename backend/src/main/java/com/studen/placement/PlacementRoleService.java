package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles and the Role -> Skill mapping — the entry point of the whole placement data model, since
 * every later phase (assessment configuration, skill gaps, learning recommendations, prep series)
 * starts from "which skills does this role require?".
 *
 * <p>Assumes ADMIN has already been checked by {@code @PreAuthorize} on the admin controller, the
 * same posture {@code AdminResourceService} takes. The read methods are also used by the
 * student-facing catalog controller, which is why they are not admin-specific.
 *
 * <p>Skills are never created here: a mapping can only point at a skill that already exists in the
 * shared catalog, so placement can never fork a second skill list.
 */
@Service
public class PlacementRoleService {

    private final PlacementRoleRepository roleRepository;
    private final RoleSkillRepository roleSkillRepository;
    private final SkillRepository skillRepository;
    private final PlacementProfileRepository profileRepository;
    private final PlacementAssessmentRepository assessmentRepository;
    private final PlacementSeriesRepository seriesRepository;

    public PlacementRoleService(PlacementRoleRepository roleRepository, RoleSkillRepository roleSkillRepository,
            SkillRepository skillRepository, PlacementProfileRepository profileRepository,
            PlacementAssessmentRepository assessmentRepository, PlacementSeriesRepository seriesRepository) {
        this.roleRepository = roleRepository;
        this.roleSkillRepository = roleSkillRepository;
        this.skillRepository = skillRepository;
        this.profileRepository = profileRepository;
        this.assessmentRepository = assessmentRepository;
        this.seriesRepository = seriesRepository;
    }

    @Transactional(readOnly = true)
    public List<PlacementRoleResponse> list(PlacementCatalogStatus status) {
        List<PlacementRole> roles = status == null
                ? roleRepository.findAllByOrderByDisplayOrderAscNameAsc()
                : roleRepository.findAllByStatusOrderByDisplayOrderAscNameAsc(status);

        Map<UUID, Long> skillCounts = new HashMap<>();
        for (IdCountView view : roleRepository.countSkillsByRole()) {
            skillCounts.put(view.id(), view.count());
        }
        return roles.stream()
                .map(role -> PlacementRoleResponse.from(role, skillCounts.getOrDefault(role.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlacementRoleDetailResponse get(UUID id) {
        PlacementRole role = findRole(id);
        return PlacementRoleDetailResponse.from(role, roleRepository.findRoleSkills(id));
    }

    @Transactional(readOnly = true)
    public List<RoleSkillResponse> listRoleSkills(UUID roleId) {
        requireRoleExists(roleId);
        return roleRepository.findRoleSkills(roleId).stream().map(RoleSkillResponse::from).toList();
    }

    @Transactional
    public PlacementRoleDetailResponse create(PlacementRoleRequest request) {
        String name = request.name().trim();
        String normalized = normalize(name);
        if (roleRepository.existsByNormalizedName(normalized)) {
            throw new ConflictException("A role with this name already exists");
        }
        PlacementRole role = new PlacementRole(name, normalized, trimToNull(request.description()));
        if (request.displayOrder() != null) {
            role.setDisplayOrder(request.displayOrder());
        }
        PlacementRole saved = roleRepository.save(role);
        return PlacementRoleDetailResponse.from(saved, List.of());
    }

    @Transactional
    public PlacementRoleDetailResponse update(UUID id, PlacementRoleRequest request) {
        PlacementRole role = findRole(id);
        String name = request.name().trim();
        String normalized = normalize(name);
        roleRepository.findByNormalizedName(normalized).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("A role with this name already exists");
            }
        });
        role.setName(name);
        role.setNormalizedName(normalized);
        role.setDescription(trimToNull(request.description()));
        if (request.displayOrder() != null) {
            role.setDisplayOrder(request.displayOrder());
        }
        return PlacementRoleDetailResponse.from(role, roleRepository.findRoleSkills(id));
    }

    /**
     * Deactivating hides a role from students without touching anything that references it;
     * deleting is only allowed while nothing does. A role a student has already targeted, or that
     * an assessment or series is built for, must never vanish from under that content.
     */
    @Transactional
    public void delete(UUID id) {
        PlacementRole role = findRole(id);
        if (profileRepository.existsByTargetRoleId(id)) {
            throw new ConflictException("Students have already chosen this role as their target — deactivate it instead");
        }
        if (assessmentRepository.existsByRoleId(id)) {
            throw new ConflictException("An assessment is configured for this role — deactivate it instead");
        }
        if (seriesRepository.existsByTargetRoleId(id)) {
            throw new ConflictException("A placement series targets this role — deactivate it instead");
        }
        // Cascades only to this role own placement_role_skills rows; the skills themselves stay.
        roleRepository.delete(role);
    }

    @Transactional
    public PlacementRoleDetailResponse setStatus(UUID id, PlacementCatalogStatus status) {
        PlacementRole role = findRole(id);
        role.setStatus(status);
        return PlacementRoleDetailResponse.from(role, roleRepository.findRoleSkills(id));
    }

    /**
     * Replaces the whole required-skill set for a role in one call, which is how an admin mapping
     * screen saves. Existing rows for skills that survive the edit are updated in place so their
     * ids stay stable.
     */
    @Transactional
    public List<RoleSkillResponse> replaceRoleSkills(UUID roleId, RoleSkillsRequest request) {
        PlacementRole role = findRole(roleId);

        Set<UUID> requestedSkillIds = new HashSet<>();
        for (RoleSkillRequest entry : request.skills()) {
            if (!requestedSkillIds.add(entry.skillId())) {
                throw new InvalidRequestException("The same skill was mapped to this role more than once");
            }
        }
        Map<UUID, Skill> skills = loadSkills(requestedSkillIds);

        Map<UUID, RoleSkill> existingBySkillId = new HashMap<>();
        for (RoleSkill existing : role.getRoleSkills()) {
            existingBySkillId.put(existing.getSkill().getId(), existing);
        }

        List<RoleSkill> updated = new ArrayList<>();
        for (RoleSkillRequest entry : request.skills()) {
            RoleSkill roleSkill = existingBySkillId.get(entry.skillId());
            if (roleSkill == null) {
                roleSkill = new RoleSkill(role, skills.get(entry.skillId()), 1, null, 0);
            }
            applyRequest(roleSkill, entry);
            updated.add(roleSkill);
        }

        // orphanRemoval deletes whatever is no longer in the list.
        role.getRoleSkills().clear();
        role.getRoleSkills().addAll(updated);
        roleRepository.flush();

        return roleRepository.findRoleSkills(roleId).stream().map(RoleSkillResponse::from).toList();
    }

    @Transactional
    public RoleSkillResponse addRoleSkill(UUID roleId, RoleSkillRequest request) {
        PlacementRole role = findRole(roleId);
        if (roleSkillRepository.findByRoleIdAndSkillId(roleId, request.skillId()).isPresent()) {
            throw new ConflictException("This skill is already mapped to this role");
        }
        Skill skill = loadSkills(Set.of(request.skillId())).get(request.skillId());
        RoleSkill roleSkill = new RoleSkill(role, skill, 1, null, 0);
        applyRequest(roleSkill, request);
        return RoleSkillResponse.from(roleSkillRepository.save(roleSkill));
    }

    @Transactional
    public void removeRoleSkill(UUID roleId, UUID skillId) {
        requireRoleExists(roleId);
        RoleSkill roleSkill = roleSkillRepository.findByRoleIdAndSkillId(roleId, skillId)
                .orElseThrow(() -> new ResourceNotFoundException("This skill is not mapped to this role"));
        roleSkillRepository.delete(roleSkill);
    }

    private void applyRequest(RoleSkill roleSkill, RoleSkillRequest request) {
        roleSkill.setWeight(request.weight() == null ? 1 : request.weight());
        roleSkill.setRequiredProficiency(request.requiredProficiency());
        roleSkill.setPriority(request.priority() == null ? 0 : request.priority());
    }

    private Map<UUID, Skill> loadSkills(Set<UUID> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Skill> found = new HashMap<>();
        for (Skill skill : skillRepository.findAllByIdIn(skillIds)) {
            found.put(skill.getId(), skill);
        }
        for (UUID requested : skillIds) {
            if (!found.containsKey(requested)) {
                throw new ResourceNotFoundException("Skill not found: " + requested);
            }
        }
        return found;
    }

    private PlacementRole findRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private void requireRoleExists(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Role not found");
        }
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
