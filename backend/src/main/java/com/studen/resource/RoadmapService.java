package com.studen.resource;

import com.studen.common.tag.ParsedTag;
import com.studen.common.tag.TagParser;
import com.studen.portfolio.EligibilityState;
import com.studen.portfolio.PortfolioSkillProfileService;
import com.studen.portfolio.StudentSkillProfile;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
 * Turns the same weak-area/resource data {@link ResourceMatchingService} already computes into an
 * ordered, per-skill roadmap plus a single deterministic "what should I learn next" recommendation.
 * Purely additive/read-only over existing sources — {@link WeakAreaAggregationService} for weakness
 * + severity, {@link ResourceRepository}/{@link StudentResourceProgressRepository} for resources and
 * real completion state, {@link com.studen.common.tag.TagParser} for topic matching. No new
 * progress system, no AI/ML ranking, no invented dependency graph: since no prerequisite metadata
 * exists anywhere in this codebase, ordering falls back to severity (worst percentage first) then
 * the existing alphabetical order {@code SkillResultService.buildSummary} already produces for
 * {@code needsImprovementTopics} — both real, already-existing signals, never fabricated.
 */
@Service
public class RoadmapService {

    private final PortfolioSkillProfileService skillProfileService;
    private final WeakAreaAggregationService weakAreaAggregationService;
    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;

    public RoadmapService(PortfolioSkillProfileService skillProfileService,
            WeakAreaAggregationService weakAreaAggregationService, ResourceRepository resourceRepository,
            StudentResourceProgressRepository progressRepository) {
        this.skillProfileService = skillProfileService;
        this.weakAreaAggregationService = weakAreaAggregationService;
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional(readOnly = true)
    public RoadmapResponse computeRoadmap(UUID userId) {
        StudentSkillProfile profile = skillProfileService.resolve(userId);
        if (!profile.hasPortfolio()) {
            return RoadmapResponse.empty(EligibilityState.NO_PORTFOLIO);
        }
        if (!profile.hasSkills()) {
            return RoadmapResponse.empty(EligibilityState.NO_SKILLS);
        }

        List<WeakAreaView> weakAreas = weakAreaAggregationService.resolveWeakAreas(userId);
        if (weakAreas.isEmpty()) {
            // Eligible, but genuinely nothing weak (no terminal assessments, or strong everywhere) —
            // TEST 9: never fabricate a personalized recommendation here.
            return RoadmapResponse.empty(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
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
        Map<UUID, StudentResourceProgress> progressByResource = progressRepository
                .findAllByStudentIdAndResourceIdIn(userId, candidateIds).stream()
                .collect(Collectors.toMap(p -> p.getResource().getId(), p -> p));

        List<RoadmapSkillGroupResponse> groups = new ArrayList<>();
        List<RoadmapItemResponse> eligibleForNextUp = new ArrayList<>();
        int topicsCompleted = 0;
        int topicsTotal = 0;

        for (Map.Entry<UUID, List<WeakAreaView>> entry : bySkill.entrySet()) {
            UUID skillId = entry.getKey();
            List<WeakAreaView> areasForSkill = entry.getValue();
            String skillName = areasForSkill.get(0).skillName();
            List<Resource> skillCandidates = candidatesBySkill.getOrDefault(skillId, List.of());

            OrderedTopics ordered = orderTopics(areasForSkill, skillName);

            List<RoadmapItemResponse> items = new ArrayList<>();
            int position = 0;
            for (String topic : ordered.topics()) {
                int percentage = ordered.percentageByTopic().get(topic);
                List<Resource> topicResources = ordered.skillScopedFallback() ? skillCandidates
                        : skillCandidates.stream()
                                .filter(r -> TagParser.anyTagMatchesTopic(r.getTags(), topic))
                                .toList();

                int total = topicResources.size();
                int completed = (int) topicResources.stream().filter(r -> isCompleted(r, progressByResource)).count();
                Resource reference = pickReferenceResource(topicResources, progressByResource);
                ResourceProgressStatus status = deriveTopicStatus(topicResources, progressByResource, total, completed);
                RecommendationPriority priority = priorityFor(status, completed, total);
                String reason = buildReason(topic, skillName, percentage, status, position);
                ResourceCardResponse resourceCard = reference == null ? null : cardFor(reference, progressByResource);

                RoadmapItemResponse item = new RoadmapItemResponse(skillId, skillName, topic, percentage, status,
                        priority, reason, resourceCard, completed, total);
                items.add(item);
                if (status != ResourceProgressStatus.COMPLETED) {
                    eligibleForNextUp.add(item);
                }
                if (total > 0 && completed == total) {
                    topicsCompleted++;
                }
                topicsTotal++;
                position++;
            }
            groups.add(new RoadmapSkillGroupResponse(skillId, skillName, items));
        }

        RoadmapItemResponse nextUp = eligibleForNextUp.stream().min(RoadmapItemResponse.PRIORITY_ORDER).orElse(null);

        RoadmapOverviewResponse overview = new RoadmapOverviewResponse(topicsCompleted, topicsTotal,
                topicsTotal == 0 ? 0 : Math.round(topicsCompleted * 100f / topicsTotal), computeStreak(userId));
        return new RoadmapResponse(EligibilityState.HAS_AVAILABLE_ASSESSMENTS, groups, overview, nextUp == null,
                nextUp);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommendations(UUID userId) {
        RoadmapResponse roadmap = computeRoadmap(userId);
        if (roadmap.nextUp() != null) {
            return new RecommendationResponse(roadmap.nextUp(), null);
        }
        if (roadmap.groups().isEmpty()) {
            return RecommendationResponse.none(
                    "No weak areas identified yet — take an assessment to get personalized recommendations.");
        }
        return RecommendationResponse
                .none("You've completed every resource in your personalized learning path. Great work!");
    }

    @Transactional(readOnly = true)
    public RoadmapOverviewResponse progress(UUID userId) {
        return computeRoadmap(userId).overview();
    }

    private record OrderedTopics(List<String> topics, Map<String, Integer> percentageByTopic,
            boolean skillScopedFallback) {
    }

    // Tag-scoped weak areas parse into individual topics (min percentage wins when the same topic
    // appears under more than one weak tag), ordered worst-percentage-first with the tag-scoped
    // areas' own already-alphabetical discovery order (see SkillResultService.buildSummary) as the
    // deterministic tiebreak. A skill whose weak signal is entirely skill-scoped (practical-sourced,
    // no tags) falls back to one skill-level "topic" — mirrors
    // ResourceMatchingService.topicBreakdown()'s identical fallback, so Focus Areas and the roadmap
    // never disagree about what "no topic signal" means for the same skill.
    private OrderedTopics orderTopics(List<WeakAreaView> areasForSkill, String skillName) {
        LinkedHashMap<String, Integer> percentageByTopic = new LinkedHashMap<>();
        for (WeakAreaView area : areasForSkill) {
            if (!area.tagScoped()) {
                continue;
            }
            ParsedTag parsed = TagParser.parse(area.tagOrLabel());
            for (String topic : parsed.topics()) {
                percentageByTopic.merge(topic, area.percentage(), Math::min);
            }
        }
        if (percentageByTopic.isEmpty()) {
            int worst = areasForSkill.stream().mapToInt(WeakAreaView::percentage).min().orElse(0);
            return new OrderedTopics(List.of(skillName), Map.of(skillName, worst), true);
        }
        List<String> ordered = percentageByTopic.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
        return new OrderedTopics(ordered, percentageByTopic, false);
    }

    // Consecutive calendar days (UTC — the app has no per-user timezone setting, same convention
    // as every other Instant field) ending today or yesterday with at least one real resource
    // completion. Deliberately UTC and completedAt-only (not LearningSession.completedAt directly)
    // so this stays inside com.studen.resource: CalendarService.complete() already writes through
    // to StudentResourceProgress via ResourceService.complete() for every resource-backed session,
    // so this single source still reflects calendar-driven completions.
    private int computeStreak(UUID userId) {
        Set<LocalDate> activeDays = new HashSet<>();
        for (var completedAt : progressRepository.findCompletedDates(userId)) {
            activeDays.add(completedAt.atZone(ZoneOffset.UTC).toLocalDate());
        }
        if (activeDays.isEmpty()) {
            return 0;
        }
        LocalDate cursor = LocalDate.now(ZoneOffset.UTC);
        if (!activeDays.contains(cursor)) {
            cursor = cursor.minusDays(1);
            if (!activeDays.contains(cursor)) {
                return 0;
            }
        }
        int streak = 0;
        while (activeDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private boolean isCompleted(Resource resource, Map<UUID, StudentResourceProgress> progressByResource) {
        StudentResourceProgress p = progressByResource.get(resource.getId());
        return p != null && p.getStatus() == ResourceProgressStatus.COMPLETED;
    }

    // Prefers a not-yet-completed resource (so the roadmap item points at something actionable),
    // then the most specific one (fewest tags — a resource narrowly about this topic over a
    // broader multi-topic one), then title, for a fully deterministic pick with no ranking model
    // duplicating ResourceMatchingService.score() (that method solves a different problem — ranking
    // a whole skill's candidate list — not "single best resource for one already-known topic").
    private Resource pickReferenceResource(List<Resource> resources,
            Map<UUID, StudentResourceProgress> progressByResource) {
        return resources.stream()
                .sorted(Comparator.comparing((Resource r) -> isCompleted(r, progressByResource))
                        .thenComparingInt(r -> r.getTags().size())
                        .thenComparing(Resource::getTitle))
                .findFirst()
                .orElse(null);
    }

    private ResourceProgressStatus deriveTopicStatus(List<Resource> topicResources,
            Map<UUID, StudentResourceProgress> progressByResource, int total, int completed) {
        if (total > 0 && completed == total) {
            return ResourceProgressStatus.COMPLETED;
        }
        boolean anyStarted = topicResources.stream().anyMatch(r -> progressByResource.containsKey(r.getId()));
        return anyStarted ? ResourceProgressStatus.IN_PROGRESS : ResourceProgressStatus.NOT_STARTED;
    }

    // HIGH = very weak + not started; MEDIUM = weak + in progress, under half its matched resources
    // done; LOW = weak but already mostly completed (revision territory) or fully completed (never
    // actually surfaced as "next" regardless of this label, since callers filter COMPLETED out of
    // the next-up candidate pool before ranking).
    private RecommendationPriority priorityFor(ResourceProgressStatus status, int completed, int total) {
        if (status == ResourceProgressStatus.NOT_STARTED) {
            return RecommendationPriority.HIGH;
        }
        if (status == ResourceProgressStatus.COMPLETED) {
            return RecommendationPriority.LOW;
        }
        boolean mostlyDone = total > 0 && completed * 2 >= total;
        return mostlyDone ? RecommendationPriority.LOW : RecommendationPriority.MEDIUM;
    }

    // Every clause here is built only from real fields (topic, skill, percentage, whether this is
    // the first not-yet-done topic in the skill) — never a claim about a learning path or sequence
    // the data can't actually support.
    private String buildReason(String topic, String skillName, int percentage, ResourceProgressStatus status,
            int position) {
        String displayTopic = capitalize(topic);
        if (status == ResourceProgressStatus.COMPLETED) {
            return "You've completed the resources matched to " + displayTopic + ".";
        }
        if (status == ResourceProgressStatus.IN_PROGRESS) {
            return "You're partway through " + displayTopic + " (scored " + percentage + "% on your " + skillName
                    + " assessment).";
        }
        if (position == 0) {
            return "You performed poorly in " + displayTopic + " in your " + skillName + " assessment (" + percentage
                    + "%).";
        }
        return displayTopic + " is another identified weak area in " + skillName + " (" + percentage + "%).";
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private ResourceCardResponse cardFor(Resource resource, Map<UUID, StudentResourceProgress> progressByResource) {
        StudentResourceProgress p = progressByResource.get(resource.getId());
        ResourceProgressStatus status = p != null ? p.getStatus() : ResourceProgressStatus.NOT_STARTED;
        return ResourceCardResponse.from(resource, status, p != null ? p.getStartedAt() : null,
                p != null ? p.getCompletedAt() : null);
    }
}
