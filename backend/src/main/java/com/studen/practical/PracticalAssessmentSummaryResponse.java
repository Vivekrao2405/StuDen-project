package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.UUID;

public record PracticalAssessmentSummaryResponse(
        UUID id,
        String title,
        UUID skillId,
        String skillName,
        PracticalType practicalType,
        WorkspaceType workspaceType,
        Difficulty difficulty,
        PracticalAssessmentStatus status,
        int timeLimitMinutes,
        int version,
        int questionCount,
        Instant createdAt) {

    public static PracticalAssessmentSummaryResponse from(PracticalAssessment entity, int questionCount) {
        return new PracticalAssessmentSummaryResponse(entity.getId(), entity.getTitle(), entity.getSkill().getId(),
                entity.getSkill().getName(), entity.getPracticalType(), entity.getWorkspaceType(),
                entity.getDifficulty(), entity.getStatus(), entity.getTimeLimitMinutes(), entity.getVersion(),
                questionCount, entity.getCreatedAt());
    }
}
