package com.studen.placement;

// Phase 4: drives the "Your Placement Learning Path" section inside My Learning (spec §12) — the
// frontend must render a specific guidance message for each non-terminal state, never a generic
// empty state, and must never fabricate recommendations.
public enum PlacementLearningPlanState {
    // No PlacementProfile, no published readiness assessment for the target role, or no completed
    // attempt yet — the student must complete a readiness assessment first.
    NO_READINESS_ASSESSMENT,
    // A readiness result exists but every required skill is at/above the Strong threshold — nothing
    // to recommend, and nothing should be invented.
    NO_SKILL_GAPS,
    // Real skill gaps exist, but zero published resources are mapped (primary or additional skill)
    // to any of them yet.
    GAPS_WITHOUT_RESOURCES,
    // At least one gap has at least one matched resource.
    HAS_RECOMMENDATIONS
}
