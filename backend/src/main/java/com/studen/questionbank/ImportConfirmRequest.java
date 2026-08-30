package com.studen.questionbank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ImportConfirmRequest(

        // Fallback skill for any question that doesn't already carry its own resolved skill (see
        // ImportedQuestionDraft.skillId) — the only skill source for the original "## Q1" template,
        // optional for the newer "## QUESTION" template where each question names its own SKILL.
        // QuestionImportService rejects any question that ends up with neither.
        UUID skillId,

        UUID topicId,

        @NotEmpty(message = "At least one question is required to import")
        @Valid
        List<ImportedQuestionDraft> questions) {
}
