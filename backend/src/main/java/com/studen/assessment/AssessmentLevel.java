package com.studen.assessment;

// Phase 7.3's assessment-derived skill level — deliberately NOT named SkillLevel, which already
// exists (com.studen.skill.SkillLevel: BEGINNER/INTERMEDIATE/EXPERT) as the student's own
// self-declared portfolio level. This is a distinct, wider 5-tier scale computed from a knowledge
// assessment's scorePercentage — see ScoringProperties.levelFor. An assessment level is never
// written back onto a portfolio Skill and never marks anything "verified" (spec §20/§21).
public enum AssessmentLevel {
    BEGINNER,
    DEVELOPING,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}
