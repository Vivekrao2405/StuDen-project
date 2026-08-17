package com.studen.practical.judge;

import java.util.Locale;

/**
 * Defense-in-depth guard on top of the already-isolated, throwaway, single-use SQL sandbox:
 * rejects anything but a single top-level SELECT/CTE statement before it's even written to a
 * container's input file, so a student can't stack extra statements after their query via the
 * generated {@code \copy (...) to ...} wrapper (see {@code sql-entrypoint.sh}).
 */
public final class SqlStatementGuard {

    private SqlStatementGuard() {
    }

    /** @return a safe, student-facing rejection reason, or {@code null} if the query is acceptable. */
    public static String reject(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "A query is required.";
        }
        String trimmed = rawQuery.strip();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        if (trimmed.contains(";")) {
            return "Only a single SELECT statement is allowed -- remove any additional statements.";
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            return "Only SELECT queries are allowed.";
        }
        return null;
    }
}
