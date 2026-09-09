package com.studen.placement;

import jakarta.validation.constraints.Min;
import java.util.UUID;

// Exactly one of questionId/practicalAssessmentId must be set (enforced in
// PlacementAssessmentService, not here — a bean-validation cross-field check would need a custom
// annotation for one field pair, not worth it for a single call site).
public record PlacementAssessmentQuestionRequest(

        UUID questionId,

        UUID practicalAssessmentId,

        @Min(value = 0, message = "Display order cannot be negative")
        Integer displayOrder,

        @Min(value = 1, message = "Points must be at least 1")
        Integer points) {
}
