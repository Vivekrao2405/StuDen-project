package com.studen.questionbank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TopicRequest(

        @NotNull(message = "Skill is required")
        UUID skillId,

        @NotBlank(message = "Topic name is required")
        @Size(max = 255, message = "Topic name must be at most 255 characters")
        String name) {
}
