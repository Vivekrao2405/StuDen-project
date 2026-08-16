package com.studen.assessment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 7.3's single source of truth for skill-level and topic-performance thresholds — spec §23
 * explicitly forbids hardcoding these anywhere else. Both {@link #levelFor} and
 * {@link #topicTierFor} are the only places in the codebase allowed to branch on a raw percentage
 * against a threshold; every other class asks one of these two methods instead.
 *
 * <p>Defaults match the spec exactly: 0-39 BEGINNER, 40-59 DEVELOPING, 60-79 INTERMEDIATE,
 * 80-89 ADVANCED, 90-100 EXPERT; topics: 0-59 NEEDS_IMPROVEMENT, 60-79 DEVELOPING, 80-100 STRONG.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringProperties {

    private int developingMin = 40;
    private int intermediateMin = 60;
    private int advancedMin = 80;
    private int expertMin = 90;

    private int topicDevelopingMin = 60;
    private int topicStrongMin = 80;

    public AssessmentLevel levelFor(int scorePercentage) {
        if (scorePercentage >= expertMin) {
            return AssessmentLevel.EXPERT;
        }
        if (scorePercentage >= advancedMin) {
            return AssessmentLevel.ADVANCED;
        }
        if (scorePercentage >= intermediateMin) {
            return AssessmentLevel.INTERMEDIATE;
        }
        if (scorePercentage >= developingMin) {
            return AssessmentLevel.DEVELOPING;
        }
        return AssessmentLevel.BEGINNER;
    }

    public TopicPerformanceTier topicTierFor(int percentage) {
        if (percentage >= topicStrongMin) {
            return TopicPerformanceTier.STRONG;
        }
        if (percentage >= topicDevelopingMin) {
            return TopicPerformanceTier.DEVELOPING;
        }
        return TopicPerformanceTier.NEEDS_IMPROVEMENT;
    }
}
