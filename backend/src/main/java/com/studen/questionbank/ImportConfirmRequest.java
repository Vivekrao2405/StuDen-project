package com.studen.questionbank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ImportConfirmRequest(

        @NotNull(message = "Skill is required")
        UUID skillId,

        UUID topicId,

        @NotEmpty(message = "At least one question is required to import")
        @Valid
        List<ImportedQuestionDraft> questions) {
}
