package com.studen.practical;

import com.studen.practical.execution.TestOutcomeStatus;
import java.util.UUID;

// Admin-only shape -- unlike ExecutionTestResultResponse, includes hidden test cases and their
// full input/expected/actual detail. Never returned from a student-facing endpoint.
public record AdminExecutionTestResultResponse(UUID testCaseId, boolean hidden, boolean passed, String input,
        String expectedOutput, String actualOutput, Long executionTimeMs, TestOutcomeStatus status) {
}
