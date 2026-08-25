package com.studen.executionserver;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exempted from {@link ApiKeyAuthFilter} -- {@code RemoteCodeExecutionService.isAvailable()} on
 * the main backend polls this before every submission, same posture as the main backend's own
 * public {@code /actuator/health} (reveals nothing beyond up/down).
 */
@RestController
public class HealthController {

    private final DockerExecutionService executionService;

    public HealthController(DockerExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return executionService.isAvailable() ? HealthResponse.up() : HealthResponse.down();
    }
}
