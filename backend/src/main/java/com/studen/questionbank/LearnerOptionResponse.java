package com.studen.questionbank;

import java.util.UUID;

// Learner-facing — NEVER add isCorrect here. This type exists specifically so the answer key can
// never leak through the learner API regardless of what QuestionOptionResponse does.
public record LearnerOptionResponse(UUID id, String optionText, int displayOrder) {

    public static LearnerOptionResponse from(QuestionOption option) {
        return new LearnerOptionResponse(option.getId(), option.getOptionText(), option.getDisplayOrder());
    }
}
