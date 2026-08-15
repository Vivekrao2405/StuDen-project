package com.studen.assessment;

import java.util.UUID;

public record AssessmentResultOptionView(UUID id, String optionText, int displayOrder, boolean correct) {

    public static AssessmentResultOptionView from(AssessmentQuestionOption option) {
        return new AssessmentResultOptionView(option.getQuestionOption().getId(),
                option.getQuestionOption().getOptionText(), option.getDisplayOrder(),
                option.getQuestionOption().isCorrect());
    }
}
