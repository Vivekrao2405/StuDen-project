package com.studen.placement;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

// The whole placement profile in one payload, so onboarding saves once and a returning student
// updates the same row instead of creating a second profile. Every list is a list of ids or enum
// values, never a delimited string.
public record PlacementProfileRequest(

        @NotNull(message = "Target role is required")
        UUID targetRoleId,

        @NotNull(message = "Experience level is required")
        ExperienceLevel experienceLevel,

        List<CompanyType> companyTypes,

        List<UUID> targetCompanyIds,

        List<UUID> currentSkillIds) {
}
