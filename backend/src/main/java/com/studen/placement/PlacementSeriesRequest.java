package com.studen.placement;

import com.studen.questionbank.Difficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// companyId and companyType are both optional: a series can be role-only, company-specific, or
// scoped to a whole company type. targetRoleId is required because a prep series always prepares
// for a role.
public record PlacementSeriesRequest(

        @NotBlank(message = "Series name is required")
        @Size(max = 200, message = "Series name must be at most 200 characters")
        String name,

        String description,

        UUID companyId,

        @NotNull(message = "Target role is required")
        UUID targetRoleId,

        CompanyType companyType,

        @NotNull(message = "Difficulty is required")
        Difficulty difficulty,

        @Min(value = 1, message = "Duration must be at least 1 hour")
        Integer estimatedDurationHours,

        String thumbnailUrl,

        List<UUID> skillIds,

        PreparationType preparationType) {
}
