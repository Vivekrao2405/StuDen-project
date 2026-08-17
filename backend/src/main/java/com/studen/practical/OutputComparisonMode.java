package com.studen.practical;

// Per-test-case configurable output comparison (spec: never blindly trim everything). Applied by
// com.studen.practical.execution.OutputComparator -- the only place allowed to implement this
// logic, mirroring ScoringProperties.levelFor's "one place branches on this" convention.
public enum OutputComparisonMode {
    // Byte-for-byte, including trailing whitespace/newlines.
    EXACT,
    // Strips leading/trailing whitespace on the whole output before comparing.
    TRIM_WHITESPACE,
    // Normalizes CRLF/CR to LF and strips trailing blank lines/trailing-line whitespace, but
    // preserves meaningful internal spacing -- the default, matching what a human reviewer
    // tolerates when eyeballing stdout.
    NORMALIZE_NEWLINES
}
