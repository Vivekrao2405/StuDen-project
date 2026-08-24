package com.studen.resource;

import com.studen.portfolio.EligibilityState;
import com.studen.portfolio.PortfolioSkillProfileService;
import com.studen.portfolio.StudentSkillProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranks published resources against a student's weak areas (spec §9's four-tier priority: exact
 * tag match, then multiple weak-tag matches, then same-skill-with-tags, then same-skill-with-no-
 * tags), grouped by weak skill and capped per group so a student is never flooded. Deterministic
 * only — no AI/ML ranking (spec §35).
 */
@Service
public class ResourceMatchingService {

    private final PortfolioSkillProfileService skillProfileService;
    private final WeakAreaAggregationService weakAreaAggregationService;
    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;
    private final LearningProperties learningProperties;

    public ResourceMatchingService(PortfolioSkillProfileService skillProfileService,
            WeakAreaAggregationService weakAreaAggregationService, ResourceRepository resourceRepository,
            StudentResourceProgressRepository progressRepository, LearningProperties learningProperties) {
        this.skillProfileService = skillProfileService;
        this.weakAreaAggregationService = weakAreaAggregationService;
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.learningProperties = learningProperties;
    }

    @Transactional(readOnly = true)
    public MyLearningResponse myLearning(UUID userId) {
        StudentSkillProfile profile = skillProfileService.resolve(userId);
        if (!profile.hasPortfolio()) {
            return new MyLearningResponse(EligibilityState.NO_PORTFOLIO, List.of());
        }
        if (!profile.hasSkills()) {
            return new MyLearningResponse(EligibilityState.NO_SKILLS, List.of());
        }

        List<WeakAreaView> weakAreas = weakAreaAggregationService.resolveWeakAreas(userId);
        if (weakAreas.isEmpty()) {
            // Eligible, but nothing weak found yet (no terminal assessments, or genuinely strong
            // everywhere) — a data outcome, not a distinct eligibility gate; the frontend reads
            // an empty `groups` list under this state as "no weak areas yet".
            return new MyLearningResponse(EligibilityState.HAS_AVAILABLE_ASSESSMENTS, List.of());
        }

        Map<UUID, List<WeakAreaView>> bySkill = new LinkedHashMap<>();
        for (WeakAreaView area : weakAreas) {
            bySkill.computeIfAbsent(area.skillId(), k -> new ArrayList<>()).add(area);
        }

        List<Resource> candidates = resourceRepository.findPublishedForSkills(bySkill.keySet());
        Map<UUID, List<Resource>> candidatesBySkill = new LinkedHashMap<>();
        for (Resource resource : candidates) {
            candidatesBySkill.computeIfAbsent(resource.getSkill().getId(), k -> new ArrayList<>()).add(resource);
        }

        Set<UUID> candidateIds = candidates.stream().map(Resource::getId).collect(Collectors.toSet());
        Map<UUID, ResourceProgressStatus> progressByResource = progressRepository
                .findAllByStudentIdAndResourceIdIn(userId, candidateIds).stream()
                .collect(Collectors.toMap(p -> p.getResource().getId(), StudentResourceProgress::getStatus));

        List<WeakAreaGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<WeakAreaView>> entry : bySkill.entrySet()) {
            UUID skillId = entry.getKey();
            List<WeakAreaView> areasForSkill = entry.getValue();
            Set<String> weakTags = areasForSkill.stream()
                    .filter(WeakAreaView::tagScoped)
                    .map(WeakAreaView::tagOrLabel)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            int worstPercentage = areasForSkill.stream().mapToInt(WeakAreaView::percentage).min().orElse(0);

            List<Resource> ranked = candidatesBySkill.getOrDefault(skillId, List.of()).stream()
                    .sorted(Comparator.comparingInt((Resource r) -> -score(r, weakTags)).thenComparing(Resource::getTitle))
                    .limit(learningProperties.getMaxResourcesPerGroup())
                    .toList();

            List<ResourceCardResponse> cards = ranked.stream()
                    .map(r -> ResourceCardResponse.from(r,
                            progressByResource.getOrDefault(r.getId(), ResourceProgressStatus.NOT_STARTED)))
                    .toList();
            int completed = (int) ranked.stream()
                    .filter(r -> progressByResource.get(r.getId()) == ResourceProgressStatus.COMPLETED)
                    .count();

            groups.add(new WeakAreaGroupResponse(skillId, areasForSkill.get(0).skillName(), List.copyOf(weakTags),
                    worstPercentage, cards, completed, cards.size()));
        }

        return new MyLearningResponse(EligibilityState.HAS_AVAILABLE_ASSESSMENTS, groups);
    }

    // Spec §9's four-tier priority collapsed into one monotonic score: any exact weak-tag overlap
    // always outranks a same-skill-only match (tier 10+ vs tier 1-2), and within that top tier,
    // more overlapping weak tags ranks higher ("multiple matching weak tags" as a finer-grained
    // tiebreak) — never excludes a same-skill resource just for having zero tag overlap (spec §8:
    // "same skill, no exact tag" is ranked lower, not dropped).
    private int score(Resource resource, Set<String> weakTags) {
        if (!weakTags.isEmpty()) {
            long overlap = resource.getTags().stream().filter(weakTags::contains).count();
            if (overlap > 0) {
                return 10 + (int) overlap;
            }
        }
        return resource.getTags().isEmpty() ? 1 : 2;
    }
}
