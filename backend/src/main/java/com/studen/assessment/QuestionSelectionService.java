package com.studen.assessment;

import com.studen.common.exception.ConflictException;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionRepository.IdDifficultyProjection;
import com.studen.questionbank.QuestionRepository.IdDifficultyTagProjection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks which PUBLISHED questions go into a newly generated assessment. Deliberately works off a
 * lightweight id+difficulty projection (never the full {@code Question} entity graph) until the
 * final selected subset is known, per the "don't load the whole bank" performance constraint.
 */
@Service
public class QuestionSelectionService {

    private final QuestionRepository questionRepository;
    private final AssessmentProperties properties;

    public QuestionSelectionService(QuestionRepository questionRepository, AssessmentProperties properties) {
        this.questionRepository = questionRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public int countPublished(UUID skillId) {
        return questionRepository.findPublishedIdAndDifficultyForSkill(skillId).size();
    }

    @Transactional(readOnly = true)
    public boolean isAssessable(UUID skillId) {
        return countPublished(skillId) >= properties.getDefaultQuestionCount();
    }

    /**
     * Returns a randomized, ordered list of PUBLISHED question IDs for {@code skillId} sized
     * exactly {@code total}: sampled per-difficulty according to the configured ratios, with any
     * per-bucket shortfall gracefully filled from the remaining published pool (never from another
     * skill, never from a non-PUBLISHED question — the source query already guarantees both).
     *
     * <p>Within each difficulty bucket, picks are spread round-robin across every distinct
     * tag/topic present (see {@link #pickAcrossGroups}) rather than a single shuffle-then-take
     * over the whole bucket — otherwise a bucket dominated by one tag (e.g. a bulk-imported bank
     * where "easy" happens to be mostly "java-basics") could fill an entire assessment from that
     * one tag purely by chance, even though the skill's other tags/topics have plenty of published
     * questions too. A skill with only one tag (or none) behaves exactly as before — round-robin
     * over a single group degrades to the same shuffle-then-take.
     */
    @Transactional(readOnly = true)
    public List<UUID> select(UUID skillId, int total) {
        List<IdDifficultyTagProjection> all = questionRepository.findPublishedIdDifficultyTagForSkill(skillId);
        if (all.size() < total) {
            throw new ConflictException("Not enough published questions are available for this assessment yet.");
        }

        Map<Difficulty, List<IdDifficultyTagProjection>> byDifficulty = all.stream()
                .collect(Collectors.groupingBy(IdDifficultyTagProjection::getDifficulty));
        Map<Difficulty, Integer> distribution = DifficultyDistributionCalculator.compute(total,
                properties.getEasyRatio(), properties.getMediumRatio(), properties.getHardRatio());

        Set<UUID> selected = new LinkedHashSet<>();
        for (Difficulty difficulty : Difficulty.values()) {
            List<IdDifficultyTagProjection> pool = byDifficulty.getOrDefault(difficulty, List.of());
            int need = distribution.getOrDefault(difficulty, 0);
            selected.addAll(pickAcrossGroups(groupByTagOrTopic(pool), Math.min(need, pool.size())));
        }

        if (selected.size() < total) {
            List<IdDifficultyTagProjection> remaining = all.stream()
                    .filter(p -> !selected.contains(p.getId()))
                    .collect(Collectors.toList());
            int need = total - selected.size();
            selected.addAll(pickAcrossGroups(groupByTagOrTopic(remaining), need));
        }

        List<UUID> ordered = new ArrayList<>(selected);
        Collections.shuffle(ordered, ThreadLocalRandom.current());
        return ordered;
    }

    // Tag takes priority over topic as the grouping key (bulk-imported banks always set a tag,
    // rarely a topic); a question with neither falls into one shared "ungrouped" bucket.
    private Map<String, List<UUID>> groupByTagOrTopic(List<IdDifficultyTagProjection> pool) {
        Map<String, List<UUID>> groups = new LinkedHashMap<>();
        for (IdDifficultyTagProjection p : pool) {
            String key = p.getTag() != null ? "tag:" + p.getTag()
                    : p.getTopicId() != null ? "topic:" + p.getTopicId()
                    : "ungrouped";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p.getId());
        }
        return groups;
    }

    // Classic round robin: one pick per group per lap (each group's own order shuffled first, and
    // the lap order over groups shuffled too, so no tag is systematically favored) until `need` is
    // reached or every group is exhausted.
    private List<UUID> pickAcrossGroups(Map<String, List<UUID>> groupedIds, int need) {
        List<Deque<UUID>> pools = new ArrayList<>();
        for (List<UUID> ids : groupedIds.values()) {
            List<UUID> copy = new ArrayList<>(ids);
            Collections.shuffle(copy, ThreadLocalRandom.current());
            pools.add(new ArrayDeque<>(copy));
        }
        Collections.shuffle(pools, ThreadLocalRandom.current());

        List<UUID> picked = new ArrayList<>();
        boolean progress = true;
        while (picked.size() < need && progress) {
            progress = false;
            for (Deque<UUID> pool : pools) {
                if (picked.size() >= need) break;
                UUID next = pool.poll();
                if (next != null) {
                    picked.add(next);
                    progress = true;
                }
            }
        }
        return picked;
    }
}
