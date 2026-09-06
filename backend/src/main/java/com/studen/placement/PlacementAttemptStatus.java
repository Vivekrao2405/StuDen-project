package com.studen.placement;

// Mirrors com.studen.assessment.AssessmentStatus: IN_PROGRESS -> SUBMITTED (student-initiated) or
// EXPIRED (backend-detected timeout). Both terminal. Phase 0 only stores the value; the engine
// that transitions it belongs to Phase 3.
public enum PlacementAttemptStatus {
    IN_PROGRESS,
    SUBMITTED,
    EXPIRED
}
