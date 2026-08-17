package com.studen.practical.judge;

// Outcome of one execute-with-stdin container run, before output is compared against the expected
// value (that comparison -- PASSED vs WRONG_ANSWER -- happens one layer up, in
// com.studen.practical.execution.ExecutionOrchestrator, since it needs the test case's expected
// output/comparison mode, which this low-level judge layer never sees).
public enum RunStatus {
    SUCCESS,
    RUNTIME_ERROR,
    TIMEOUT,
    MEMORY_LIMIT,
    OUTPUT_LIMIT,
    SYSTEM_ERROR
}
