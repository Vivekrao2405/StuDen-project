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
    //   4/5. Same skill, with or without unrelated tags — never excluded, only ranked lowest.
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
