package com.studen.integrity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Admin manual review (goal #23/#18/#17) -- reason is required so every override is
// self-documenting; recorded with admin id + timestamp by AdminIntegrityService#override.
public record IntegrityOverrideRequest(
        @NotNull IntegrityStatus status,
        @NotBlank String reason) {
}
