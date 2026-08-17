package com.studen.practical.judge;

public record RunOutcome(RunStatus status, String stdout, String stderr, long durationMs) {

    public static RunOutcome systemError() {
        return new RunOutcome(RunStatus.SYSTEM_ERROR, null, null, 0);
    }
}
