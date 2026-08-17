package com.studen.practical.judge;

// Result of one compile step. `success=true, stderr=null` for an interpreted language (Python's
// py_compile syntax check passing) as well as a genuinely successful compile -- callers only
// branch on `success`, never on language.
public record CompileOutcome(boolean success, String stderr, long durationMs) {

    public static CompileOutcome success(long durationMs) {
        return new CompileOutcome(true, null, durationMs);
    }

    public static CompileOutcome failure(String stderr, long durationMs) {
        return new CompileOutcome(false, stderr, durationMs);
    }
}
