package com.studen.placement;

import com.studen.assessment.AssessmentLevel;
import java.util.UUID;

// One row of "Skill Breakdown" — score is always plain correct/total for the questions on THIS
// attempt that measured this skill (never points-weighted, matching the existing assessment
// scoring convention). `requiredProficiency`/`meetsRequiredProficiency` are null/true when the
// role has no configured target for this skill (RoleSkill.requiredProficiency is optional).
public record PlacementSkillBreakdownView(UUID skillId, String skillName, int correctCount, int totalQuestions,
        int scorePercentage, SkillReadinessStatus status, AssessmentLevel requiredProficiency,
        boolean meetsRequiredProficiency) {
}
