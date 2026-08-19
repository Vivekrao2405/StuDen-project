package com.studen.integrity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HeartbeatRequest(@NotBlank @Size(max = 100) String sessionId) {
}
