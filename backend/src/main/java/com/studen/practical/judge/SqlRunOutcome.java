package com.studen.practical.judge;

/**
 * Result of running a student's query and the admin's reference query against the same freshly
 * seeded, throwaway sandbox. {@code studentCsv}/{@code referenceCsv} are raw CSV text (header +
 * rows) -- {@code com.studen.practical.execution.SqlResultComparator} does the actual diffing;
 * this type only reports what happened when the queries ran.
 */
public record SqlRunOutcome(SqlRunStatus status, String studentCsv, String referenceCsv, String errorMessage,
        long durationMs) {

    public static SqlRunOutcome systemError() {
        return new SqlRunOutcome(SqlRunStatus.SYSTEM_ERROR, null, null, null, 0);
    }

    public static SqlRunOutcome rejected(String reason) {
        return new SqlRunOutcome(SqlRunStatus.REJECTED, null, null, reason, 0);
    }
}
