package com.studen.practical.execution;

/**
 * Terminal/in-flight states for one {@link ExecutionJob}. SYSTEM_ERROR/CANCELLED are
 * infrastructure-side outcomes -- {@code com.studen.practical.PracticalAttemptService} must never
 * treat these as a student-code failure (never scores, never finalizes an attempt on them; safe to
 * retry). SECURITY_ERROR (the sandbox guard rejected the submission itself -- e.g. a stacked SQL
 * statement) is deliberately NOT an infrastructure failure: it's a genuine, student-caused terminal
 * outcome and scores like any other failed test. Every other terminal status is likewise a
 * safely-classified result of running the student's own submission.
 */
public enum ExecutionJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    TIMEOUT,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    MEMORY_LIMIT,
    OUTPUT_LIMIT,
    SECURITY_ERROR,
    SYSTEM_ERROR,
    CANCELLED;

    public boolean isInfrastructureFailure() {
        return this == SYSTEM_ERROR || this == CANCELLED;
    }
}
