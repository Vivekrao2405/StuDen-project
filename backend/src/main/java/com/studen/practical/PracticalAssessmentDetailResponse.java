package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Admin-only detail shape — includes hidden test cases and configurationJson raw text. Never
// returned from a student-facing endpoint; see StudentPracticalAssessmentResponse for that shape.
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
        String requirements,
        String constraints,
        EvaluationType evaluationType,
        PracticalAssessmentStatus status,
        int version,
        UUID previousVersionId,
        String configurationJson,
        List<PracticalCodingLanguageResponse> languages,
        List<PracticalTestCaseResponse> testCases,
        List<PracticalRubricCriterionResponse> rubricCriteria,
        Instant createdAt) {

    public static PracticalAssessmentDetailResponse from(PracticalAssessment entity,
            List<PracticalCodingLanguage> languages, List<PracticalTestCase> testCases,
            List<PracticalRubricCriterion> rubricCriteria) {
        return new PracticalAssessmentDetailResponse(
                entity.getId(), entity.getTitle(), entity.getSkill().getId(), entity.getSkill().getName(),
                entity.getPracticalType(), entity.getWorkspaceType(), entity.getDifficulty(),
                entity.getTimeLimitMinutes(), entity.getInstructions(), entity.getRequirements(),
                entity.getConstraints(), entity.getEvaluationType(), entity.getStatus(), entity.getVersion(),
                entity.getPreviousVersion() != null ? entity.getPreviousVersion().getId() : null,
                entity.getConfigurationJson(),
                languages.stream().map(PracticalCodingLanguageResponse::from).toList(),
                testCases.stream().map(PracticalTestCaseResponse::from).toList(),
                rubricCriteria.stream().map(PracticalRubricCriterionResponse::from).toList(),
                entity.getCreatedAt());
    }
}
