package com.studen.questionbank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// No `status` field, deliberately — mirrors ServiceRequest's convention: the only way a question
// reaches PUBLISHED is the dedicated transition endpoints, which run full validation first.
// Cross-field rules (option count/correct-answer count per questionType) are enforced by
// QuestionValidationService, not Bean Validation annotations here.
public record QuestionRequest(

        @NotNull(message = "Skill is required")
        UUID skillId,

        UUID topicId,

        @NotBlank(message = "Question text is required")
        @Size(max = 2000, message = "Question text must be at most 2000 characters")
        String questionText,

        @NotNull(message = "Question type is required")
        QuestionType questionType,

        @NotNull(message = "Difficulty is required")
        Difficulty difficulty,

        @Size(max = 4000, message = "Explanation must be at most 4000 characters")
        String explanation,

        @Positive(message = "Estimated time must be greater than zero")
        Integer estimatedTimeSeconds,

        Set<@NotBlank @Size(max = 50) String> tags,

        @Valid
        List<QuestionOptionRequest> options) {
}
