package com.studen.placement;

import com.studen.skill.Skill;
import java.util.UUID;

// One skill a series covers, plus (when the student has a measured score for it) the same
// readiness classification Phase 3 already computes — never a second threshold system. Section 14
// of the spec: "if the student has identified skill gaps, Placement Prep should prioritize/
// recommend relevant preparation content" — this is the badge that lets a series card/detail say
// "Needs attention" vs "Strong" without inventing new scoring.
public record PlacementSkillCoverageView(
        UUID skillId,
        String skillName,
        SkillReadinessStatus readinessStatus,
        Integer scorePercentage) {

    public static PlacementSkillCoverageView from(Skill skill, Integer scorePercentage, PlacementScoringProperties scoring) {
        return new PlacementSkillCoverageView(skill.getId(), skill.getName(),
                scorePercentage == null ? null : scoring.statusFor(scorePercentage), scorePercentage);
    }
}
