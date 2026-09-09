package com.studen.placement;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.resource.LearningProperties;
import com.studen.resource.Resource;
import com.studen.resource.ResourceCardResponse;
import com.studen.resource.ResourceProgressStatus;
import com.studen.resource.ResourceRepository;
import com.studen.resource.StudentResourceProgress;
import com.studen.resource.StudentResourceProgressRepository;
import com.studen.skill.Skill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 4: turns a student's already-computed, already-ranked Placement priority skill gaps
 * ({@link PlacementReadinessService}, Phase 3 — never re-derived or re-sorted here) into a
 * personalized learning plan by matching each gap's skill against published, admin-mapped
 * {@code com.studen.resource.Resource}s (primary skill or {@code Resource.additionalSkills}, Phase
 * 4's new relational mapping — never free-text tag matching, and never "title contains the skill
 * name"). This is a read-only *view* over Phase 3 + the existing resource catalog — no new
 * progress/recommendation table is created; "starting" a recommended resource is the exact same
 * {@code StudentResourceProgress} row the existing My Learning flow already produces.
 *
 * <p>Deduplication (spec §17): a resource mapped to several gap skills is only ever shown once,
 * under the highest-ranked (most urgent) gap it matches — later, lower-priority gaps in the same
 * plan skip it.
 */
@Service
public class PlacementLearningPlanService {

    private final PlacementReadinessService placementReadinessService;
    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;
    private final LearningProperties learningProperties;

    public PlacementLearningPlanService(PlacementReadinessService placementReadinessService,
            ResourceRepository resourceRepository, StudentResourceProgressRepository progressRepository,
            LearningProperties learningProperties) {
        this.placementReadinessService = placementReadinessService;
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.learningProperties = learningProperties;
    }

    @Transactional(readOnly = true)
    public PlacementLearningPlanResponse getLearningPlan(UUID studentId) {
        PlacementReadinessStatusResponse status = placementReadinessService.getStatus(studentId);
        if (status.state() != PlacementReadinessState.ASSESSMENT_AVAILABLE || status.latestResult() == null) {
            return PlacementLearningPlanResponse.empty(PlacementLearningPlanState.NO_READINESS_ASSESSMENT,
                    status.targetRoleId(), status.targetRoleName());
        }

        PlacementReadinessResultResponse result = status.latestResult();
        if (result.priorityGaps().isEmpty()) {
            return PlacementLearningPlanResponse.empty(PlacementLearningPlanState.NO_SKILL_GAPS, status.targetRoleId(),
                    status.targetRoleName());
        }

        Map<UUID, List<Resource>> candidatesBySkill = candidatesBySkill(result.priorityGaps());
        Map<UUID, StudentResourceProgress> progressByResource = progressFor(studentId, candidatesBySkill);

        Set<UUID> usedResourceIds = new HashSet<>();
        List<PlacementSkillPlanResponse> plan = new ArrayList<>();
        int totalResources = 0;
        for (PlacementSkillGapView gap : result.priorityGaps()) {
            List<Resource> ranked = rankedUnusedResources(candidatesBySkill.getOrDefault(gap.skillId(), List.of()),
                    usedResourceIds);
            ranked.forEach(r -> usedResourceIds.add(r.getId()));
            totalResources += ranked.size();
            plan.add(new PlacementSkillPlanResponse(gap.rank(), gap.skillId(), gap.skillName(), gap.scorePercentage(),
                    gap.status(), gap.roleSkillPriority(), gap.roleSkillWeight(), toCards(ranked, progressByResource)));
        }

        PlacementLearningPlanState state = totalResources == 0 ? PlacementLearningPlanState.GAPS_WITHOUT_RESOURCES
                : PlacementLearningPlanState.HAS_RECOMMENDATIONS;
        return new PlacementLearningPlanResponse(state, status.targetRoleId(), status.targetRoleName(),
                result.attemptId(), result.submittedAt(), plan);
    }

    // Recommendations for one specific gap skill — refuses (404, IDOR-safe posture matching
    // PlacementReadinessService) unless that skill is actually one of the caller's own current
    // priority gaps, so a student can never browse arbitrary skills through this endpoint.
    @Transactional(readOnly = true)
    public List<ResourceCardResponse> getRecommendationsForSkill(UUID studentId, UUID skillId) {
        PlacementReadinessResultResponse result = placementReadinessService.latestForStudent(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No readiness result available"));
        boolean isCurrentGap = result.priorityGaps().stream().anyMatch(g -> g.skillId().equals(skillId));
        if (!isCurrentGap) {
            throw new ResourceNotFoundException("That skill is not one of your current priority gaps");
        }

        List<Resource> candidates = resourceRepository.findPublishedForSkillsIncludingAdditional(Set.of(skillId));
        List<Resource> ranked = rankedUnusedResources(candidates, new HashSet<>());
        Map<UUID, StudentResourceProgress> progressByResource = progressFor(studentId,
                Map.of(skillId, ranked));
        return toCards(ranked, progressByResource);
    }

    private Map<UUID, List<Resource>> candidatesBySkill(List<PlacementSkillGapView> gaps) {
        Set<UUID> skillIds = gaps.stream().map(PlacementSkillGapView::skillId).collect(Collectors.toSet());
        List<Resource> candidates = resourceRepository.findPublishedForSkillsIncludingAdditional(skillIds);

        Map<UUID, List<Resource>> bySkill = new LinkedHashMap<>();
        for (Resource resource : candidates) {
            if (skillIds.contains(resource.getSkill().getId())) {
                bySkill.computeIfAbsent(resource.getSkill().getId(), k -> new ArrayList<>()).add(resource);
            }
            for (Skill extra : resource.getAdditionalSkills()) {
                if (skillIds.contains(extra.getId())) {
                    bySkill.computeIfAbsent(extra.getId(), k -> new ArrayList<>()).add(resource);
                }
            }
        }
        return bySkill;
    }

    private List<Resource> rankedUnusedResources(List<Resource> candidates, Set<UUID> usedResourceIds) {
        return candidates.stream()
                .filter(r -> !usedResourceIds.contains(r.getId()))
                .sorted(Comparator
                        .comparingInt((Resource r) -> r.getDifficulty() == null ? Integer.MAX_VALUE : r.getDifficulty().ordinal())
                        .thenComparing(Resource::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(learningProperties.getMaxResourcesPerGroup())
                .toList();
    }

    private Map<UUID, StudentResourceProgress> progressFor(UUID studentId, Map<UUID, List<Resource>> bySkill) {
        Set<UUID> resourceIds = bySkill.values().stream().flatMap(List::stream).map(Resource::getId)
                .collect(Collectors.toSet());
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return progressRepository.findAllByStudentIdAndResourceIdIn(studentId, resourceIds).stream()
                .collect(Collectors.toMap(p -> p.getResource().getId(), p -> p));
    }

    private List<ResourceCardResponse> toCards(List<Resource> resources, Map<UUID, StudentResourceProgress> progressByResource) {
        return resources.stream()
                .map(r -> {
                    StudentResourceProgress p = progressByResource.get(r.getId());
                    ResourceProgressStatus status = p != null ? p.getStatus() : ResourceProgressStatus.NOT_STARTED;
                    return ResourceCardResponse.from(r, status, p != null ? p.getStartedAt() : null,
                            p != null ? p.getCompletedAt() : null);
                })
                .toList();
    }
}
