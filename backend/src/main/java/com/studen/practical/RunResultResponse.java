package com.studen.practical;

import com.studen.practical.execution.ExecutionJobStatus;
import java.util.List;

/**
 * Response shape for {@code POST /practical-attempts/{id}/run} -- public test cases only, hidden
 * tests collapsed to a count. {@code message} is always safe, student-facing text; {@code
 * compileError} is the actual (sanitized) compiler/interpreter stderr, present only on
 * {@code COMPILATION_ERROR}. See {@code com.studen.practical.PracticalAttemptService} for how this
 * is built from an {@code execution.PracticalExecutionResult}.
 */
public record RunResultResponse(
        ExecutionJobStatus status,
        String message,
        String compileError,
        Integer testsPassed,
        Integer testsTotal,
        Integer hiddenTestsPassed,
        Integer hiddenTestsTotal,
        Long durationMs,
        List<ExecutionTestResultResponse> publicTestResults) {
}
