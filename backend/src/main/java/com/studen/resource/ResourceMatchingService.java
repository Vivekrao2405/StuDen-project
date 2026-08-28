package com.studen.resource;

import com.studen.common.tag.ParsedTag;
import com.studen.common.tag.TagParser;
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
 *
 * <p><b>Topic-scoped exclusion:</b> whenever a group's weak tags parse out at least one real topic
 * (e.g. weak tag {@code python-lists} → topic {@code lists}), a resource carrying its own different,
 * specific topic (e.g. a resource tagged only {@code python-variables}) is excluded outright rather
 * than merely ranked last — showing "Python Variables" as a recommendation for a "Lists" weakness is
 * actively misleading, not just low-relevance. A resource with no topic claim of its own (untagged,
 * or a bare-language tag like {@code python}) carries no contradicting signal and still fills in as
 * before. This exclusion only applies once real topic signal exists; a skill-scoped-only weak area
 * (no parseable topics at all — see {@link #topicBreakdown}) keeps the full original fallback, since
 * there is no finer signal available to filter on.
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
            return new MyLearningResponse(EligibilityState.NO_PORTFOLIO, List.of(), LearningOverviewResponse.empty());
        }
        if (!profile.hasSkills()) {
            return new MyLearningResponse(EligibilityState.NO_SKILLS, List.of(), LearningOverviewResponse.empty());
        }

        List<WeakAreaView> weakAreas = weakAreaAggregationService.resolveWeakAreas(userId);
        int assessmentsCompletedCount = weakAreaAggregationService.countCompletedAssessments(userId);
        if (weakAreas.isEmpty()) {
            // Eligible, but nothing weak found yet (no terminal assessments, or genuinely strong
            // everywhere) — a data outcome, not a distinct eligibility gate; the frontend reads
            // an empty `groups` list under this state as "no weak areas yet".
            return new MyLearningResponse(EligibilityState.HAS_AVAILABLE_ASSESSMENTS, List.of(),
                    new LearningOverviewResponse(0, 0, assessmentsCompletedCount, 0, 0));
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

        List<WeakAreaGroupResponse> groups = new ArrayList<>();
        int totalResourcesCount = 0;
        int totalCompletedCount = 0;
        int totalTotalCount = 0;
        int totalWeakTopicsCount = 0;
        for (Map.Entry<UUID, List<WeakAreaView>> entry : bySkill.entrySet()) {
            UUID skillId = entry.getKey();
            List<WeakAreaView> areasForSkill = entry.getValue();
            String skillName = areasForSkill.get(0).skillName();
            Set<String> weakTags = areasForSkill.stream()
                    .filter(WeakAreaView::tagScoped)
                    .map(WeakAreaView::tagOrLabel)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            int worstPercentage = areasForSkill.stream().mapToInt(WeakAreaView::percentage).min().orElse(0);

            // Every topic segment parsed out of every weak tag for this skill (language segment
            // excluded — see TagParser). Non-empty here means we have real topic-level signal, which
            // both the resource filter below and topicBreakdown() key off of.
            Set<String> weakTopics = new LinkedHashSet<>();
            for (String weakTag : weakTags) {
                weakTopics.addAll(TagParser.parse(weakTag).topics());
            }

            List<Resource> allSkillCandidates = candidatesBySkill.getOrDefault(skillId, List.of());
            List<Resource> ranked = allSkillCandidates.stream()
                    .filter(r -> weakTopics.isEmpty() || score(r, weakTags) != SAME_SKILL_WITH_TAGS)
                    .sorted(Comparator.comparingInt((Resource r) -> -score(r, weakTags)).thenComparing(Resource::getTitle))
                    .limit(learningProperties.getMaxResourcesPerGroup())
                    .toList();

            List<ResourceCardResponse> cards = ranked.stream()
                    .map(r -> {
                        StudentResourceProgress p = progressByResource.get(r.getId());
                        ResourceProgressStatus status = p != null ? p.getStatus() : ResourceProgressStatus.NOT_STARTED;
                        return ResourceCardResponse.from(r, status, p != null ? p.getStartedAt() : null,
                                p != null ? p.getCompletedAt() : null);
                    })
                    .toList();
            int completed = (int) ranked.stream()
                    .filter(r -> progressByResource.containsKey(r.getId())
                            && progressByResource.get(r.getId()).getStatus() == ResourceProgressStatus.COMPLETED)
                    .count();

            List<FocusAreaTopicResponse> topics = topicBreakdown(weakTopics, areasForSkill, skillName,
                    worstPercentage, allSkillCandidates, progressByResource);

            groups.add(new WeakAreaGroupResponse(skillId, skillName, List.copyOf(weakTags),
                    worstPercentage, cards, completed, cards.size(), topics));

            totalResourcesCount += cards.size();
            totalCompletedCount += completed;
            totalTotalCount += cards.size();
            totalWeakTopicsCount += topics.size();
        }

        LearningOverviewResponse overview = new LearningOverviewResponse(totalWeakTopicsCount, totalResourcesCount,
                assessmentsCompletedCount, totalCompletedCount, totalTotalCount);
        return new MyLearningResponse(EligibilityState.HAS_AVAILABLE_ASSESSMENTS, groups, overview);
    }

    // Breaks a skill's weak tags down into individual topics (TagParser) and, for each, counts real
    // completed/total against the *full* published-resource set for the skill — not the capped
    // `resources` list above, so a skill with several weak topics doesn't have its per-topic totals
    // starved by the "don't flood the student" cap. A topic is shown even when zero resources
    // currently match it (0/0, an honest "nothing published for this yet" state) — a real weakness
    // must never be hidden from Focus Areas just because the resource library hasn't caught up.
    // When no weak tag parses out any topic at all (skill-scoped-only signal, e.g. a practical-
    // sourced weak area, or a bare/non-hyphenated bucket name), falls back to a single skill-level
    // entry so Focus Areas is never silently empty while a real weakness exists.
    private List<FocusAreaTopicResponse> topicBreakdown(Set<String> weakTopics, List<WeakAreaView> areasForSkill,
            String skillName, int worstPercentage, List<Resource> allSkillCandidates,
            Map<UUID, StudentResourceProgress> progressByResource) {
        if (weakTopics.isEmpty()) {
            if (areasForSkill.isEmpty()) {
                return List.of();
            }
            int total = allSkillCandidates.size();
            int completed = (int) allSkillCandidates.stream()
                    .filter(r -> progressByResource.containsKey(r.getId())
                            && progressByResource.get(r.getId()).getStatus() == ResourceProgressStatus.COMPLETED)
                    .count();
            return List.of(new FocusAreaTopicResponse(skillName, worstPercentage, completed, total));
        }

        Map<String, Integer> topicPercentage = new LinkedHashMap<>();
        for (WeakAreaView area : areasForSkill) {
            if (!area.tagScoped()) {
                continue;
            }
            ParsedTag parsed = TagParser.parse(area.tagOrLabel());
            for (String topic : parsed.topics()) {
                topicPercentage.merge(topic, area.percentage(), Math::min);
            }
        }

        List<FocusAreaTopicResponse> result = new ArrayList<>();
        for (Map.Entry<String, Integer> topicEntry : topicPercentage.entrySet()) {
            String topic = topicEntry.getKey();
            int total = 0;
            int completed = 0;
            for (Resource resource : allSkillCandidates) {
                if (!TagParser.anyTagMatchesTopic(resource.getTags(), topic)) {
                    continue;
                }
                total++;
                StudentResourceProgress p = progressByResource.get(resource.getId());
                if (p != null && p.getStatus() == ResourceProgressStatus.COMPLETED) {
                    completed++;
                }
            }
            result.add(new FocusAreaTopicResponse(topic, topicEntry.getValue(), completed, total));
        }
        return result;
    }

    // Spec §9's four-tier priority, extended with TagParser-based topic matching so a resource
    // doesn't need the exact composite weak tag to count as relevant — see class javadoc's example
    // (python-lists tag vs. python-lists-loops-references weak tag). Tiers, highest first:
    //   1. EXACT_TAG: resource has the literal weak-tag string as one of its own tags.
    //   2. TOPIC_MATCH: resource's tags share at least one parsed topic with a weak tag's topics
    //      (e.g. resource "python-lists" vs. weak "python-lists-loops-references" — both parse to
    //      language "python", and "lists" is in both topic sets). More overlapping topics ranks
    //      higher, same "multiple matching weak tags" tiebreak as before.
    //   3. LANGUAGE_ONLY: resource carries only the bare language tag (e.g. "python", no topics) —
    //      a general resource for that language, per the request's "broader" resource support.
    //   4/5. Same skill, with or without unrelated tags — ranked lowest, and tier 4 (SAME_SKILL_
    //      WITH_TAGS, a resource with its own different specific topic) is filtered out entirely by
    //      the caller once real weak-topic signal exists (see class javadoc). Tier 5 (untagged) is
    //      never excluded — it makes no contradicting topic claim.
    private static final int EXACT_TAG_BASE = 100;
    private static final int TOPIC_MATCH_BASE = 50;
    private static final int LANGUAGE_ONLY_MATCH = 10;
    private static final int SAME_SKILL_WITH_TAGS = 2;
    private static final int SAME_SKILL_NO_TAGS = 1;

    private int score(Resource resource, Set<String> weakTags) {
        if (weakTags.isEmpty()) {
            return resource.getTags().isEmpty() ? SAME_SKILL_NO_TAGS : SAME_SKILL_WITH_TAGS;
        }

        long exactOverlap = resource.getTags().stream().filter(weakTags::contains).count();
        if (exactOverlap > 0) {
            return EXACT_TAG_BASE + (int) exactOverlap;
        }

        Set<String> weakTopics = new LinkedHashSet<>();
        Set<String> weakLanguages = new LinkedHashSet<>();
        for (String weakTag : weakTags) {
            ParsedTag parsed = TagParser.parse(weakTag);
            if (parsed.language() != null) {
                weakLanguages.add(parsed.language());
            }
            weakTopics.addAll(parsed.topics());
        }

        Set<String> matchedTopics = new LinkedHashSet<>();
        boolean languageOnlyMatch = false;
        for (String tag : resource.getTags()) {
            ParsedTag parsed = TagParser.parse(tag);
            if (parsed.language() == null) {
                continue;
            }
            if (!parsed.topics().isEmpty()) {
                parsed.topics().stream().filter(weakTopics::contains).forEach(matchedTopics::add);
            } else if (weakLanguages.contains(parsed.language())) {
                languageOnlyMatch = true;
            }
        }

        if (!matchedTopics.isEmpty()) {
            return TOPIC_MATCH_BASE + matchedTopics.size();
        }
        if (languageOnlyMatch) {
            return LANGUAGE_ONLY_MATCH;
        }
        return resource.getTags().isEmpty() ? SAME_SKILL_NO_TAGS : SAME_SKILL_WITH_TAGS;
    }
}
