package com.studen.executionserver;

public record CompileResponse(boolean success, String stderr, long durationMs) {
}
