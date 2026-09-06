package com.studen.placement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PlacementAssessmentQuestionRequest(

        @NotNull(message = "Question is required")
        UUID questionId,

        @Min(value = 0, message = "Display order cannot be negative")
        Integer displayOrder,

        @Min(value = 1, message = "Points must be at least 1")
        Integer points) {
}
