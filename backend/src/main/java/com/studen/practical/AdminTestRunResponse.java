package com.studen.practical;

import com.studen.practical.execution.ExecutionJobStatus;
import java.util.List;

public record AdminTestRunResponse(ExecutionJobStatus status, String message, String compileError,
        Integer testsPassed, Integer testsTotal, Long durationMs, List<AdminExecutionTestResultResponse> testResults) {
}
