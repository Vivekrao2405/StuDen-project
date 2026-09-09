package com.studen.placement;

import com.studen.questionbank.QuestionOption;
import java.util.UUID;

public record PlacementAttemptResultOptionView(UUID id, String optionText, int displayOrder, boolean correct) {

    public static PlacementAttemptResultOptionView from(QuestionOption option) {
        return new PlacementAttemptResultOptionView(option.getId(), option.getOptionText(), option.getDisplayOrder(),
                option.isCorrect());
    }
}
