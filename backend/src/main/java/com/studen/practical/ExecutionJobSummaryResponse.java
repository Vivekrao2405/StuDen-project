package com.studen.practical;

import com.studen.practical.execution.ExecutionJob;
import com.studen.practical.execution.ExecutionJobKind;
import com.studen.practical.execution.ExecutionJobStatus;
import java.time.Instant;
import java.util.UUID;

// Run-history row -- "Run #1 Compilation Error, Run #2 3/10, Run #3 10/10..." (spec). No source
// code, no per-test detail -- see PracticalAttemptController's dedicated executions endpoint for
// that, gated the same way as every other attempt lookup (owner-only).
public record ExecutionJobSummaryResponse(UUID id, ExecutionJobKind kind, ExecutionJobStatus status,
        Integer testsPassed, Integer testsTotal, Long durationMs, Instant createdAt) {

    public static ExecutionJobSummaryResponse from(ExecutionJob job) {
        return new ExecutionJobSummaryResponse(job.getId(), job.getKind(), job.getStatus(), job.getTestsPassed(),
                job.getTestsTotal(), job.getDurationMs(), job.getCreatedAt());
    }
}
