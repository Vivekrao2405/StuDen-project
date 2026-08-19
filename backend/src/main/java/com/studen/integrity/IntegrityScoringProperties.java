package com.studen.integrity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 7.6's single source of truth for integrity deduction points, per-category deduction
 * caps, and score-to-status thresholds -- mirrors {@code com.studen.assessment.ScoringProperties}'
 * idiom exactly (spec: "implement scoring rules in a configurable backend policy... rather than
 * hardcoding calculations throughout controllers"). {@link IntegrityEventClassifier} and
 * {@link IntegrityScoringService} are the only two places allowed to read these values.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.integrity")
public class IntegrityScoringProperties {

    private int baseScore = 100;

    private int lowDeduction = 1;
    private int mediumDeduction = 3;
    private int highDeduction = 7;
    private int criticalDeduction = 15;

    // Per-category cap on total deduction -- prevents one noisy category (e.g. many short tab
    // switches) from alone driving the score to zero.
    private int tabAwayCategoryCap = 30;
    private int clipboardCategoryCap = 30;
    private int fullscreenCategoryCap = 20;
    private int multipleSessionCategoryCap = 25;
    private int navigationCategoryCap = 10;

    private int cleanMin = 90;
    private int lowConcernMin = 75;
    private int reviewMin = 50;

    // Away-duration buckets (tab-hidden/window-blur paired with its return) and occurrence
    // counts after which a repeated category escalates one severity level.
    private int awayShortMaxSeconds = 5;
    private int awayMediumMaxSeconds = 30;
    private int awayEscalateAfterCount = 4;
    private int clipboardEscalateAfterCount = 5;
    private int fullscreenEscalateAfterCount = 3;

    public int deductionFor(IntegritySeverity severity) {
        return switch (severity) {
            case INFO -> 0;
            case LOW -> lowDeduction;
            case MEDIUM -> mediumDeduction;
            case HIGH -> highDeduction;
            case CRITICAL -> criticalDeduction;
        };
    }

    public IntegrityStatus statusFor(int score) {
        if (score >= cleanMin) {
            return IntegrityStatus.CLEAN;
        }
        if (score >= lowConcernMin) {
            return IntegrityStatus.LOW_CONCERN;
        }
        if (score >= reviewMin) {
            return IntegrityStatus.REVIEW;
        }
        return IntegrityStatus.HIGH_CONCERN;
    }
}
