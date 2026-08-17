package com.studen.practical;

import com.studen.practical.execution.TestOutcomeStatus;
import java.util.UUID;

// Public-test-case detail only -- callers must never build this from a hidden ExecutionTestResult
// row. `expectedOutput` is always null for SQL (the reference query is never sent to a student,
// hidden or public); `actualOutput` for SQL is a safe row-count summary, never raw result data --
// see com.studen.practical.execution.ExecutionOrchestrator.
public record ExecutionTestResultResponse(UUID testCaseId, boolean passed, String input, String expectedOutput,
        String actualOutput, Long executionTimeMs, TestOutcomeStatus status) {
}
