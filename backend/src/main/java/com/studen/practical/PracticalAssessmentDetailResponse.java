package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Admin-only detail shape — includes hidden test cases and configurationJson raw text. Never
// returned from a student-facing endpoint; see StudentPracticalAssessmentResponse for that shape.
// `questions` (Phase 7.6) replaces the old single-question requirements/constraints/languages/
// testCases/rubricCriteria fields, already sorted by displayOrder.
public record PracticalAssessmentDetailResponse(
        UUID id,
        String title,
        UUID skillId,
        String skillName,
        PracticalType practicalType,
        WorkspaceType workspaceType,
        Difficulty difficulty,
        int timeLimitMinutes,
        String instructions,
        EvaluationType evaluationType,
        PracticalAssessmentStatus status,
        int version,
        UUID previousVersionId,
        String configurationJson,
        List<PracticalQuestionResponse> questions,
        Instant createdAt) {

    public static PracticalAssessmentDetailResponse from(PracticalAssessment entity,
            List<PracticalQuestionResponse> questions) {
        return new PracticalAssessmentDetailResponse(
                entity.getId(), entity.getTitle(), entity.getSkill().getId(), entity.getSkill().getName(),
                entity.getPracticalType(), entity.getWorkspaceType(), entity.getDifficulty(),
                entity.getTimeLimitMinutes(), entity.getInstructions(), entity.getEvaluationType(),
                entity.getStatus(), entity.getVersion(),
                entity.getPreviousVersion() != null ? entity.getPreviousVersion().getId() : null,
                entity.getConfigurationJson(), questions, entity.getCreatedAt());
    }
}
