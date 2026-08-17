package com.studen.practical.execution;

// One place for the safe, student/admin-facing summary text for a PracticalExecutionResult --
// never expose infrastructure detail (spec: no server paths/env vars/stack traces beyond the
// sanitized compileError/actualOutput fields already scoped for that).
public final class ExecutionMessages {

    private ExecutionMessages() {
    }

    public static String forResult(PracticalExecutionResult result) {
        return switch (result.status()) {
            case COMPILATION_ERROR -> "Compilation failed.";
            case SYSTEM_ERROR ->
                    "Automated execution is temporarily unavailable right now. Your work is saved — please try again shortly.";
            case SECURITY_ERROR -> "Your submission couldn't be run — it doesn't follow this question's rules.";
            case COMPLETED -> {
                if (result.testsTotal() == null || result.testsTotal() == 0) {
                    yield "Ran successfully.";
                }
                yield result.testsPassed() == result.testsTotal() ? "All tests passed."
                        : result.testsPassed() + "/" + result.testsTotal() + " tests passed.";
            }
            default -> "Execution finished.";
        };
    }
}
