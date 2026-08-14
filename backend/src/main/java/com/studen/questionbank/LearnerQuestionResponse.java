package com.studen.questionbank;

import java.util.List;
import java.util.UUID;

// Learner-facing — deliberately excludes isCorrect, explanation, status, createdBy/reviewedBy, and
// any DRAFT/REVIEW-only field. This is the exact shape Phase 7.2's Assessment Engine will consume
// (spec §30): question + options, never the answer key.
public record LearnerQuestionResponse(
        UUID id,
        UUID skillId,
        UUID topicId,
        String questionText,
        QuestionType questionType,
        Difficulty difficulty,
        Integer estimatedTimeSeconds,
        List<LearnerOptionResponse> options) {

    public static LearnerQuestionResponse from(Question question, List<QuestionOption> options) {
        return new LearnerQuestionResponse(
                question.getId(),
                question.getSkill().getId(),
                question.getTopic() == null ? null : question.getTopic().getId(),
                question.getQuestionText(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getEstimatedTimeSeconds(),
                options.stream().map(LearnerOptionResponse::from).toList());
    }
}
