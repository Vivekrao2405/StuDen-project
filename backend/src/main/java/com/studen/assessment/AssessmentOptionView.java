package com.studen.assessment;

import java.util.UUID;

// In-progress view — deliberately excludes `correct`, mirroring LearnerOptionResponse's
// structural no-leak guarantee (Phase 7.1). AssessmentResultOptionView is the post-submission
// counterpart that's allowed to carry it.
public record AssessmentOptionView(UUID id, String optionText, int displayOrder) {

    public static AssessmentOptionView from(AssessmentQuestionOption option) {
        return new AssessmentOptionView(option.getQuestionOption().getId(), option.getQuestionOption().getOptionText(),
                option.getDisplayOrder());
    }
}
