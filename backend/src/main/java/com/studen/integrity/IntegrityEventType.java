package com.studen.integrity;

// Phase 7.6 Assessment Integrity. RUN_CODE/COMPILATION_ATTEMPT/FIRST_SUCCESSFUL_COMPILATION/
// SUBMISSION are deliberately NOT event types here -- that data already exists in
// com.studen.practical.execution.ExecutionJob / PracticalAttempt.firstSuccessfulCompilationAt,
// and is read directly by AdminIntegrityService/IntegrityScoringService rather than duplicated.
// ASSESSMENT_EXPIRED is likewise not a client event -- expiry is already server-detected
// (PracticalAttemptStatus.EXPIRED) and read the same way.
//
// MULTIPLE_SESSION is server-synthesized only (see IntegrityEventService#heartbeat) -- a client
// can never submit it directly, since a browser tab can't know about another tab's session.
public enum IntegrityEventType {
    TAB_HIDDEN,
    TAB_VISIBLE,
    WINDOW_BLUR,
    WINDOW_FOCUS,
    COPY_ATTEMPT,
    PASTE_ATTEMPT,
    CUT_ATTEMPT,
    FULLSCREEN_ENTERED,
    FULLSCREEN_EXITED,
    NAVIGATION_VIOLATION,
    MULTIPLE_SESSION
}
