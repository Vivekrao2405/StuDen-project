package com.studen.questionbank;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ImportPublishRequest(

        @NotEmpty(message = "At least one question ID is required")
        List<UUID> questionIds) {
}
