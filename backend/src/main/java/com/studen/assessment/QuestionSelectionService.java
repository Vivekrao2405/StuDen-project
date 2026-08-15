package com.studen.assessment;

import com.studen.common.exception.ConflictException;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionRepository.IdDifficultyProjection;
import java.util.ArrayList;
import java.util.Collections;
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
     */
    @Transactional(readOnly = true)
    public List<UUID> select(UUID skillId, int total) {
        List<IdDifficultyProjection> all = questionRepository.findPublishedIdAndDifficultyForSkill(skillId);
        if (all.size() < total) {
            throw new ConflictException("Not enough published questions are available for this assessment yet.");
        }

        Map<Difficulty, List<UUID>> byDifficulty = all.stream()
                .collect(Collectors.groupingBy(IdDifficultyProjection::getDifficulty,
                        Collectors.mapping(IdDifficultyProjection::getId, Collectors.toList())));
        Map<Difficulty, Integer> distribution = DifficultyDistributionCalculator.compute(total,
                properties.getEasyRatio(), properties.getMediumRatio(), properties.getHardRatio());

        Set<UUID> selected = new LinkedHashSet<>();
        for (Difficulty difficulty : Difficulty.values()) {
            List<UUID> pool = new ArrayList<>(byDifficulty.getOrDefault(difficulty, List.of()));
            Collections.shuffle(pool, ThreadLocalRandom.current());
            int need = distribution.getOrDefault(difficulty, 0);
            int take = Math.min(need, pool.size());
            selected.addAll(pool.subList(0, take));
        }

        if (selected.size() < total) {
            List<UUID> remaining = all.stream()
                    .map(IdDifficultyProjection::getId)
                    .filter(id -> !selected.contains(id))
                    .collect(Collectors.toList());
            Collections.shuffle(remaining, ThreadLocalRandom.current());
            int need = total - selected.size();
            selected.addAll(remaining.subList(0, Math.min(need, remaining.size())));
        }

        List<UUID> ordered = new ArrayList<>(selected);
        Collections.shuffle(ordered, ThreadLocalRandom.current());
        return ordered;
    }
}
