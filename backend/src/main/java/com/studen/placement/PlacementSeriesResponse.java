package com.studen.placement;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.UUID;

// List-row view. moduleCount is supplied by the service so a paginated list never walks the lazy
// modules collection once per row.
public record PlacementSeriesResponse(
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
        int moduleCount,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementSeriesResponse from(PlacementSeries series, int moduleCount) {
        return new PlacementSeriesResponse(
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
                moduleCount,
                series.getCreatedAt(),
                series.getUpdatedAt());
    }
}
