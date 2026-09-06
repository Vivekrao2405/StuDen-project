package com.studen.placement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No status field, deliberately: status changes only through the dedicated activate/deactivate
// endpoints, mirroring ResourceRequest and PracticalAssessmentRequest.
public record PlacementRoleRequest(

        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name must be at most 100 characters")
        String name,

        String description,

        Integer displayOrder) {
}
