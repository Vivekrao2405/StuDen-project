package com.studen.placement;

// Drives the Placement Readiness entry screen. NO_PROFILE and NO_ASSESSMENT_AVAILABLE are normal
// states, not errors — the frontend must render a specific guidance message for each rather than a
// generic error (spec §21), and must never call POST /attempts while in either state.
public enum PlacementReadinessState {
    NO_PROFILE,
    NO_ASSESSMENT_AVAILABLE,
    ASSESSMENT_AVAILABLE
}
