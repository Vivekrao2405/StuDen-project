package com.studen.practical;

import com.studen.questionbank.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

// No `status` field, deliberately — mirrors QuestionRequest's convention: the only way a
// practical assessment reaches PUBLISHED is the dedicated transition endpoint, which runs full
// validation first (rubric totals, CODING requires >=1 language + test case).
public record PracticalAssessmentRequest(

        @NotBlank(message = "Title is required")
        String title,

        @NotNull(message = "Skill is required")
        UUID skillId,

        @NotNull(message = "Practical type is required")
        PracticalType practicalType,

        @NotNull(message = "Workspace type is required")
        WorkspaceType workspaceType,

        @NotNull(message = "Difficulty is required")
        Difficulty difficulty,

        @Positive(message = "Time limit must be greater than zero")
        int timeLimitMinutes,

        @NotBlank(message = "Instructions are required")
        String instructions,

        String requirements,

        String constraints,

        @NotNull(message = "Evaluation type is required")
        EvaluationType evaluationType,

        String configurationJson,

        @Valid
        List<PracticalCodingLanguageRequest> languages,

        @Valid
        List<PracticalTestCaseRequest> testCases,

        @Valid
        List<PracticalRubricCriterionRequest> rubricCriteria) {
}
