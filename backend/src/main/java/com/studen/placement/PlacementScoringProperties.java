package com.studen.placement;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 3's single source of truth for skill-readiness thresholds — mirrors
 * {@code com.studen.assessment.ScoringProperties}'s pattern exactly (never hardcode a percentage
 * boundary at any call site; only {@link #statusFor} may branch on one). Defaults were chosen so
 * every example score in the spec (Python 86 -> Strong, SQL 72 -> Good, Excel 78 -> Good,
 * Statistics 61 -> Improve, Power BI 45 -> Critical) lands in the labeled tier — a validated
 * default, not an arbitrary one — but remain fully env-overridable since the spec forbids treating
 * the example numbers themselves as official rules.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.placement.scoring")
public class PlacementScoringProperties {

    private int improveMin = 50;
    private int goodMin = 70;
    private int strongMin = 85;

    public SkillReadinessStatus statusFor(int scorePercentage) {
        if (scorePercentage >= strongMin) {
            return SkillReadinessStatus.STRONG;
        }
        if (scorePercentage >= goodMin) {
            return SkillReadinessStatus.GOOD;
        }
        if (scorePercentage >= improveMin) {
            return SkillReadinessStatus.IMPROVE;
        }
        return SkillReadinessStatus.CRITICAL;
    }
}
