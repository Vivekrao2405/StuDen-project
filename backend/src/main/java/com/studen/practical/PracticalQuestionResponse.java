package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.util.List;
import java.util.UUID;

// Admin-only detail shape — includes hidden test cases. Never returned from a student-facing
// endpoint; see StudentTestCaseView / the workspace-content DTOs for that shape.
public record PracticalQuestionResponse(
        UUID id,
        String title,
        UUID skillId,
        String skillName,
        Difficulty difficulty,
        String instructions,
        String requirements,
        String constraints,
        String configurationJson,
        int points,
        int displayOrder,
        List<PracticalCodingLanguageResponse> languages,
        List<PracticalTestCaseResponse> testCases,
        List<PracticalRubricCriterionResponse> rubricCriteria) {

    public static PracticalQuestionResponse from(PracticalQuestion entity, List<PracticalCodingLanguage> languages,
            List<PracticalTestCase> testCases, List<PracticalRubricCriterion> rubricCriteria) {
        return new PracticalQuestionResponse(entity.getId(), entity.getTitle(),
                entity.getSkill() != null ? entity.getSkill().getId() : null,
                entity.getSkill() != null ? entity.getSkill().getName() : null, entity.getDifficulty(),
                entity.getInstructions(), entity.getRequirements(), entity.getConstraints(),
                entity.getConfigurationJson(), entity.getPoints(), entity.getDisplayOrder(),
                languages.stream().map(PracticalCodingLanguageResponse::from).toList(),
                testCases.stream().map(PracticalTestCaseResponse::from).toList(),
                rubricCriteria.stream().map(PracticalRubricCriterionResponse::from).toList());
    }
}
