package com.studen.practical.execution;

// Per-test-case outcome recorded on ExecutionTestResult. WRONG_ANSWER: ran fine, output didn't
// match. The others mirror ExecutionJobStatus's student-caused subset, scoped to a single test.
public enum TestOutcomeStatus {
    PASSED,
    WRONG_ANSWER,
    RUNTIME_ERROR,
    TIMEOUT,
    MEMORY_LIMIT,
    OUTPUT_LIMIT
}
