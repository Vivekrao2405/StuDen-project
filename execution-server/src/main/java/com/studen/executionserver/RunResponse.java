package com.studen.executionserver;

public record RunResponse(RunStatus status, String stdout, String stderr, long durationMs) {

    public static RunResponse systemError(long durationMs) {
        return new RunResponse(RunStatus.SYSTEM_ERROR, null, null, durationMs);
    }
}
