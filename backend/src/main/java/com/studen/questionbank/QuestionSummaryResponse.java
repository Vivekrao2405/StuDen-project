package com.studen.questionbank;

import java.time.Instant;
import java.util.UUID;

// Admin list-row view — deliberately excludes options/explanation/tags to avoid fetching them for
// every row on a paginated list (spec's explicit N+1/over-fetch warning). Fetch the full
// QuestionResponse via GET /{id} for a single question's detail.
public record QuestionSummaryResponse(
        UUID id,
        UUID skillId,
        String skillName,
        UUID topicId,
        String topicName,
        String questionTextPreview,
        QuestionType questionType,
        Difficulty difficulty,
        QuestionStatus status,
        int version,
        Instant updatedAt) {

    private static final int PREVIEW_LENGTH = 140;

    public static QuestionSummaryResponse from(Question question) {
        String text = question.getQuestionText();
        String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
        return new QuestionSummaryResponse(
                question.getId(),
                question.getSkill().getId(),
                question.getSkill().getName(),
                question.getTopic() == null ? null : question.getTopic().getId(),
                question.getTopic() == null ? null : question.getTopic().getName(),
                preview,
                question.getQuestionType(),
                question.getDifficulty(),
                question.getStatus(),
                question.getVersion(),
                question.getUpdatedAt());
    }
}
