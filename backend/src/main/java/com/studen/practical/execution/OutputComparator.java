package com.studen.practical.execution;

import com.studen.practical.OutputComparisonMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place allowed to implement output comparison (spec: "do NOT blindly trim everything") --
 * mirrors {@code com.studen.assessment.ScoringProperties}'s "single place branches on this"
 * convention. {@link OutputComparisonMode#NORMALIZE_NEWLINES} is the default: it tolerates the
 * trailing-newline/CRLF differences a human reviewer would ignore while eyeballing stdout, but
 * never collapses meaningful internal spacing.
 */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String actual, String expected, OutputComparisonMode mode) {
        String a = actual == null ? "" : actual;
        String e = expected == null ? "" : expected;
        return switch (mode) {
            case EXACT -> a.equals(e);
            case TRIM_WHITESPACE -> a.strip().equals(e.strip());
            case NORMALIZE_NEWLINES -> normalize(a).equals(normalize(e));
        };
    }

    private static String normalize(String s) {
        String unified = s.replace("\r\n", "\n").replace("\r", "\n");
        String[] rawLines = unified.split("\n", -1);
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            lines.add(stripTrailing(line));
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return String.join("\n", lines);
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
