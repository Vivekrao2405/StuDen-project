package com.studen.placement;

import com.studen.questionbank.QuestionOption;
import java.util.UUID;

// In-progress view — deliberately excludes `correct`, mirroring AssessmentOptionView's structural
// no-leak guarantee. PlacementAttemptResultOptionView is the post-submission counterpart.
public record PlacementAttemptOptionView(UUID id, String optionText, int displayOrder) {

    public static PlacementAttemptOptionView from(QuestionOption option) {
        return new PlacementAttemptOptionView(option.getId(), option.getOptionText(), option.getDisplayOrder());
    }
}
