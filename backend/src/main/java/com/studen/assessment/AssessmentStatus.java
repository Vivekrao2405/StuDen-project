package com.studen.assessment;

// IN_PROGRESS -> SUBMITTED (student-initiated) or EXPIRED (backend-detected timeout, scored the
// same way as a submit). Both SUBMITTED and EXPIRED are terminal — see AssessmentService.
public enum AssessmentStatus {
    IN_PROGRESS,
    SUBMITTED,
    EXPIRED
}
