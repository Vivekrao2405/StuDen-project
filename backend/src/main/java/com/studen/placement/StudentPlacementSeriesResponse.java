package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.skill.SkillResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Catalog/list-row view for the Placement Prep discovery page. Only ever built for PUBLISHED
// series (see PlacementPrepService) — a student can never see this shape for a DRAFT/ARCHIVED one.
public record StudentPlacementSeriesResponse(
        UUID id,
        String name,
        String description,
        UUID companyId,
        String companyName,
        UUID targetRoleId,
        String targetRoleName,
        CompanyType companyType,
        PreparationType preparationType,
        Difficulty difficulty,
        Integer estimatedDurationHours,
        String thumbnailUrl,
        List<SkillResponse> skillsCovered,
        int moduleCount,
        int progressPercentage,
        PlacementProgressStatus progressStatus,
        Instant updatedAt) {
}
