package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.skill.SkillResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlacementSeriesDetailResponse(
        UUID id,
        String name,
        String description,
        UUID companyId,
        String companyName,
        UUID targetRoleId,
        String targetRoleName,
        CompanyType companyType,
        Difficulty difficulty,
        Integer estimatedDurationHours,
        String thumbnailUrl,
        PlacementContentStatus status,
        List<SkillResponse> skillsCovered,
        List<PlacementModuleResponse> modules,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementSeriesDetailResponse from(PlacementSeries series, List<PlacementModuleResponse> modules) {
        return new PlacementSeriesDetailResponse(
                series.getId(),
                series.getName(),
                series.getDescription(),
                series.getCompany() == null ? null : series.getCompany().getId(),
                series.getCompany() == null ? null : series.getCompany().getName(),
                series.getTargetRole().getId(),
                series.getTargetRole().getName(),
                series.getCompanyType(),
                series.getDifficulty(),
                series.getEstimatedDurationHours(),
                series.getThumbnailUrl(),
                series.getStatus(),
                series.getSkillsCovered().stream().map(SkillResponse::from).toList(),
                modules,
                series.getCreatedAt(),
                series.getUpdatedAt());
    }
}
