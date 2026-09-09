package com.studen.placement;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlacementAssessmentDetailResponse(
        UUID id,
        String title,
        String description,
        UUID roleId,
        String roleName,
        Difficulty difficulty,
        Integer durationMinutes,
        Integer passingScore,
        PlacementContentStatus status,
        List<PlacementAssessmentQuestionResponse> questions,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementAssessmentDetailResponse from(PlacementAssessment assessment, Set<UUID> roleSkillIds) {
        List<PlacementAssessmentQuestionResponse> questions = assessment.getQuestions().stream()
                .map(link -> PlacementAssessmentQuestionResponse.from(link, roleSkillIds))
                .toList();
        return new PlacementAssessmentDetailResponse(assessment.getId(), assessment.getTitle(),
                assessment.getDescription(), assessment.getRole().getId(), assessment.getRole().getName(),
                assessment.getDifficulty(), assessment.getDurationMinutes(), assessment.getPassingScore(),
                assessment.getStatus(), questions, assessment.getCreatedAt(), assessment.getUpdatedAt());
    }
}
