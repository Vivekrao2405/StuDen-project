package com.studen.questionbank;

import java.util.UUID;

// Admin-only view — includes isCorrect. Never reuse this for a learner-facing response; use
// LearnerOptionResponse instead.
public record QuestionOptionResponse(UUID id, String optionText, int displayOrder, boolean isCorrect) {

    public static QuestionOptionResponse from(QuestionOption option) {
        return new QuestionOptionResponse(option.getId(), option.getOptionText(), option.getDisplayOrder(),
                option.isCorrect());
    }
}
