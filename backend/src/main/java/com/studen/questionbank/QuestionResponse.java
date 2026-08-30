package com.studen.questionbank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Full admin view — includes isCorrect (via options' QuestionOptionResponse), createdBy/reviewedBy,
// status, and version chain. Never expose this shape to a learner-facing endpoint.
public record QuestionResponse(
        UUID id,
        UUID skillId,
        String skillName,
        UUID topicId,
        String topicName,
        String questionText,
        QuestionType questionType,
        Difficulty difficulty,
        String explanation,
        QuestionStatus status,
        Integer estimatedTimeSeconds,
        String tag,
        List<QuestionOptionResponse> options,
        UUID createdById,
        String createdByName,
        UUID reviewedById,
        String reviewedByName,
        int version,
        UUID previousVersionId,
        Instant createdAt,
        Instant updatedAt,
        DuplicateWarningResponse duplicateWarning) {

    // `options` is passed explicitly (batch-fetched by the caller) rather than read through
    // question.getOptions() — same lazy-collection-safety reasoning as ServiceResponse.from.
    public static QuestionResponse from(Question question, List<QuestionOption> options) {
        return from(question, options, null);
    }

    public static QuestionResponse from(Question question, List<QuestionOption> options,
            DuplicateWarningResponse duplicateWarning) {
        return new QuestionResponse(
                question.getId(),
                question.getSkill().getId(),
                question.getSkill().getName(),
                question.getTopic() == null ? null : question.getTopic().getId(),
                question.getTopic() == null ? null : question.getTopic().getName(),
                question.getQuestionText(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getExplanation(),
                question.getStatus(),
                question.getEstimatedTimeSeconds(),
                question.getTag(),
                options.stream().map(QuestionOptionResponse::from).toList(),
                question.getCreatedBy().getId(),
                question.getCreatedBy().getFullName(),
                question.getReviewedBy() == null ? null : question.getReviewedBy().getId(),
                question.getReviewedBy() == null ? null : question.getReviewedBy().getFullName(),
                question.getVersion(),
                question.getPreviousVersion() == null ? null : question.getPreviousVersion().getId(),
                question.getCreatedAt(),
                question.getUpdatedAt(),
                duplicateWarning);
    }
}
