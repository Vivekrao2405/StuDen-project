package com.studen.placement;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.UUID;

// List-row view. questionCount comes from the service so listing never touches the lazy questions
// collection once per row.
public record PlacementAssessmentResponse(
        UUID id,
        String title,
        String description,
        UUID roleId,
        String roleName,
        Difficulty difficulty,
        Integer durationMinutes,
        Integer passingScore,
        PlacementContentStatus status,
        int questionCount,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementAssessmentResponse from(PlacementAssessment assessment, int questionCount) {
        return new PlacementAssessmentResponse(assessment.getId(), assessment.getTitle(), assessment.getDescription(),
                assessment.getRole().getId(), assessment.getRole().getName(), assessment.getDifficulty(),
                assessment.getDurationMinutes(), assessment.getPassingScore(), assessment.getStatus(), questionCount,
                assessment.getCreatedAt(), assessment.getUpdatedAt());
    }
}
