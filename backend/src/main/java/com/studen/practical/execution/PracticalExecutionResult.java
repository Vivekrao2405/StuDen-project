package com.studen.practical.execution;

import java.util.List;

/**
 * In-memory result of one full grading run (compile once, run every test case) -- not yet
 * persisted. {@code com.studen.practical.PracticalAttemptService}/{@code AdminExecutionService}
 * turn this into an {@link ExecutionJob} (or, for an admin test-run, return it directly without
 * persisting). {@code compileError} is null for SQL (no compile step) and for a successful
 * coding compile.
 */
public record PracticalExecutionResult(ExecutionJobStatus status, String compileError, List<TestCaseOutcome> testResults,
        Integer testsPassed, Integer testsTotal, long durationMs) {

    // null/null -- no test genuinely ran (infra failure), distinct from a COMPLETED run that
    // happened to have zero test cases configured.
    public static PracticalExecutionResult systemError(long durationMs) {
        return new PracticalExecutionResult(ExecutionJobStatus.SYSTEM_ERROR, null, List.of(), null, null, durationMs);
    }
}
