package com.studen.placement;

import com.studen.skill.SkillResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// The full profile in one payload, with target companies and current skills expanded so an
// onboarding or dashboard screen never has to resolve ids itself.
public record PlacementProfileResponse(
        UUID id,
        UUID userId,
        UUID targetRoleId,
        String targetRoleName,
        ExperienceLevel experienceLevel,
        List<CompanyType> companyTypes,
        List<PlacementCompanyResponse> targetCompanies,
        List<String> manualTargetCompanies,
        List<SkillResponse> currentSkills,
        Instant createdAt,
        Instant updatedAt) {

    // Collections are materialized here rather than handed over lazily: open-in-view is false, so
    // Jackson serializes after the transaction has already closed.
    public static PlacementProfileResponse from(PlacementProfile profile) {
        Set<CompanyType> types = profile.getCompanyTypes();
        return new PlacementProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getTargetRole().getId(),
                profile.getTargetRole().getName(),
                profile.getExperienceLevel(),
                types.stream().toList(),
                profile.getTargetCompanies().stream().map(PlacementCompanyResponse::from).toList(),
                profile.getManualTargetCompanies().stream().map(PlacementProfileManualCompany::getName).toList(),
                profile.getCurrentSkills().stream().map(SkillResponse::from).toList(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
