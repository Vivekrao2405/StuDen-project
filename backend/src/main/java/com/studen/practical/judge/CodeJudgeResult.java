package com.studen.practical.judge;

/**
 * Result of a {@link CodeExecutionService} run/evaluate call. {@code UNAVAILABLE} is the only
 * status any implementation in this codebase currently returns — see
 * {@link UnavailableCodeExecutionService}. The other statuses exist so the interface's contract is
 * complete for a future real judge, without this phase fabricating results it can't back up.
 */
public record CodeJudgeResult(
        CodeJudgeStatus status,
        String message,
        Integer testsPassed,
        Integer testsTotal) {

    public static CodeJudgeResult unavailable(String message) {
        return new CodeJudgeResult(CodeJudgeStatus.UNAVAILABLE, message, null, null);
    }

    public enum CodeJudgeStatus {
        UNAVAILABLE,
        ACCEPTED,
        WRONG_ANSWER,
        COMPILATION_ERROR,
        RUNTIME_ERROR,
        TIME_LIMIT_EXCEEDED
    }
}
