package com.studen.executionserver;

// Mirrors com.studen.practical.judge.RunStatus on the main backend exactly -- RemoteCodeExecutionService
// there parses this enum's name() directly out of /run's JSON response, so the two must stay in sync.
public enum RunStatus {
    SUCCESS,
    RUNTIME_ERROR,
    TIMEOUT,
    MEMORY_LIMIT,
    OUTPUT_LIMIT,
    SYSTEM_ERROR
}
