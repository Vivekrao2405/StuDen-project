package com.studen.practical;

import com.studen.questionbank.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

// One question inside PracticalAssessmentRequest.questions. `id` is null for a brand-new question,
// or an existing question's id when the admin is editing/reordering/duplicating (a duplicated
// question is sent with id=null, same as a new one — see AdminPracticalAssessmentService.
// replaceQuestions for exactly how add/reorder/edit/delete/duplicate all collapse into one
// "replace the question list" operation, mirroring how languages/testCases/rubricCriteria already
// work on the pre-7.6 single-question model).
public record PracticalQuestionRequest(

        UUID id,

        @NotBlank(message = "Question title is required")
        String title,

        // Optional overrides — null means "inherit the assessment's skill/difficulty". Powers the
        // per-skill performance breakdown (spec §15) without forcing every question onto one skill.
        UUID skillId,

        Difficulty difficulty,

        @NotBlank(message = "Instructions are required")
        String instructions,

        String requirements,

        String constraints,

        String configurationJson,

        @Positive(message = "Points must be greater than zero")
        int points,

        int displayOrder,

        @Valid
        List<PracticalCodingLanguageRequest> languages,

        @Valid
        List<PracticalTestCaseRequest> testCases,

        @Valid
        List<PracticalRubricCriterionRequest> rubricCriteria) {
}
