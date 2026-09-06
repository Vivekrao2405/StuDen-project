package com.studen.placement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// Exactly one of the three id fields must match itemType. The service rejects any other
// combination before it can reach the database CHECK constraint that backs the same rule.
public record PlacementModuleItemRequest(

        @NotNull(message = "Item type is required")
        ModuleItemType itemType,

        UUID questionId,

        UUID practicalAssessmentId,

        UUID resourceId,

        @Min(value = 0, message = "Display order cannot be negative")
        Integer displayOrder,

        Boolean required) {
}
