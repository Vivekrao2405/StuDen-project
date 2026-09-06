package com.studen.placement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// requiredItemCount null means every required item must be completed; a number means at least
// that many. Phase 0 stores it without evaluating it.
public record PlacementModuleRequest(

        @NotBlank(message = "Module name is required")
        @Size(max = 200, message = "Module name must be at most 200 characters")
        String name,

        String description,

        @Min(value = 0, message = "Display order cannot be negative")
        Integer displayOrder,

        @Min(value = 1, message = "Required item count must be at least 1")
        Integer requiredItemCount) {
}
