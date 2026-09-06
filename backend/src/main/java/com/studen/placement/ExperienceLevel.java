package com.studen.placement;

// The student's self-declared starting point captured during placement onboarding. Deliberately
// separate from com.studen.skill.SkillLevel (a per-skill portfolio level) and from
// com.studen.assessment.AssessmentLevel (a measured, assessment-derived level): this one is a
// whole-person context signal, never a measurement and never written from a score.
public enum ExperienceLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
