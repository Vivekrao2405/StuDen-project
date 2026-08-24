package com.studen.practical;

// Per-question outcome inside one PracticalAttempt (spec §10). NOT_ATTEMPTED/IN_PROGRESS are
// lifecycle states before submission; COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT/MEMORY_LIMIT/PARTIAL/
// PASSED/FAILED are terminal outcomes of automated grading (CODING/SQL); UNDER_REVIEW/EVALUATED
// are the manual-grading lifecycle (MANUAL/HYBRID questions) -- mirrors PracticalAttemptStatus's
// own IN_PROGRESS -> UNDER_REVIEW -> EVALUATED shape, just scoped to one question.
public enum PracticalAttemptQuestionStatus {
    NOT_ATTEMPTED,
    IN_PROGRESS,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT,
    MEMORY_LIMIT,
    PARTIAL,
    PASSED,
    FAILED,
    UNDER_REVIEW,
    EVALUATED
}
