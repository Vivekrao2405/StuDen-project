package com.studen.placement;

// Phase 3's readiness-category label for a measured skill score, distinct from the 5-tier
// com.studen.assessment.AssessmentLevel (which measures raw knowledge, not role-readiness).
// Deliberately 4 tiers to match the spec's exact example labels (Strong/Good/Improve/Critical) —
// see PlacementScoringProperties for the configurable thresholds, never hardcoded at any call site.
public enum SkillReadinessStatus {
    CRITICAL,
    IMPROVE,
    GOOD,
    STRONG
}
