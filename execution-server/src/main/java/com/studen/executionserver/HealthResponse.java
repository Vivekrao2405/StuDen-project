package com.studen.executionserver;

public record HealthResponse(String status, String docker) {

    public static HealthResponse up() {
        return new HealthResponse("UP", "AVAILABLE");
    }

    public static HealthResponse down() {
        return new HealthResponse("DOWN", "UNAVAILABLE");
    }
}
