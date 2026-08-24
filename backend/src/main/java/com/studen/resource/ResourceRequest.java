package com.studen.resource;

import com.studen.questionbank.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

// No `status` field, deliberately — mirrors PracticalAssessmentRequest/QuestionRequest: status
// only changes via the dedicated publish/unpublish/archive endpoints. File upload for PDF/
// DOCUMENT is a separate multipart endpoint (AdminResourceController.uploadFile), not part of
// this JSON body — same split PortfolioController uses for cover-image upload vs. the rest of
// the portfolio form.
public record ResourceRequest(

        @NotBlank(message = "Title is required")
        String title,

        String description,

        @NotNull(message = "Resource type is required")
        ResourceType resourceType,

        @NotNull(message = "Skill is required")
        UUID skillId,

        Difficulty difficulty,

        Integer estimatedMinutes,

        // EXTERNAL_LINK/VIDEO only.
        String externalUrl,

        // NOTES only.
        String notesContent,

        List<String> tags) {
}
