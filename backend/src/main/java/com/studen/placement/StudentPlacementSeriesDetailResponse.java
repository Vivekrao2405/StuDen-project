package com.studen.placement;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentPlacementSeriesDetailResponse(
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
        List<PlacementSkillCoverageView> skillsCovered,
        int progressPercentage,
        PlacementProgressStatus progressStatus,
        List<StudentPlacementModuleResponse> modules,
        Instant updatedAt) {
}
