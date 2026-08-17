package com.studen.practical.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Diffs the CSV (header + rows) produced by a student's query against the admin's reference query,
 * both run against the identical seeded dataset -- this is what makes "two different valid queries
 * both pass" work, instead of a hand-maintained expected-output blob. {@code ordered=false} (the
 * default -- most practice questions don't require a specific row order) compares rows as a
 * multiset; {@code ordered=true} requires an exact row-for-row match, for questions where ordering
 * is itself part of the correct answer (e.g. "top 5 by score, highest first").
 */
public final class SqlResultComparator {

    private SqlResultComparator() {
    }

    public record ComparisonResult(boolean matches, int actualRowCount, int expectedRowCount) {
    }

    public static ComparisonResult compare(String actualCsv, String expectedCsv, boolean ordered) {
        List<String> actualLines = normalizedLines(actualCsv);
        List<String> expectedLines = normalizedLines(expectedCsv);

        String actualHeader = actualLines.isEmpty() ? "" : actualLines.get(0);
        String expectedHeader = expectedLines.isEmpty() ? "" : expectedLines.get(0);
        List<String> actualRows = actualLines.isEmpty() ? List.of() : actualLines.subList(1, actualLines.size());
        List<String> expectedRows = expectedLines.isEmpty() ? List.of() : expectedLines.subList(1, expectedLines.size());

        boolean headerMatches = actualHeader.equalsIgnoreCase(expectedHeader);
        boolean rowsMatch;
        if (!headerMatches) {
            rowsMatch = false;
        } else if (ordered) {
            rowsMatch = actualRows.equals(expectedRows);
        } else {
            List<String> sortedActual = new ArrayList<>(actualRows);
            List<String> sortedExpected = new ArrayList<>(expectedRows);
            Collections.sort(sortedActual);
            Collections.sort(sortedExpected);
            rowsMatch = sortedActual.equals(sortedExpected);
        }

        return new ComparisonResult(rowsMatch, actualRows.size(), expectedRows.size());
    }

    private static List<String> normalizedLines(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String unified = csv.replace("\r\n", "\n").replace("\r", "\n").strip();
        if (unified.isEmpty()) {
            return List.of();
        }
        return List.of(unified.split("\n", -1));
    }
}
