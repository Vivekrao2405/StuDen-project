package com.studen.practical.execution;

import java.util.UUID;

// One test case's outcome from an in-flight (not-yet-persisted) execution run. `actualOutput` is
// already display-capped and, for SQL, is a safe row-count summary rather than raw result data --
// see ExecutionOrchestrator.
public record TestCaseOutcome(UUID testCaseId, boolean hidden, boolean passed, String actualOutput,
        long executionTimeMs, TestOutcomeStatus status) {
}
