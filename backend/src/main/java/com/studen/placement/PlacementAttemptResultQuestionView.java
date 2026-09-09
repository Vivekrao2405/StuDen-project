package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.List;
import java.util.UUID;

// Post-submission only — correct answers and explanations are safe to expose here because the
// attempt is now permanently locked (no further answer mutation is possible).
public record PlacementAttemptResultQuestionView(UUID id, String questionText, QuestionType questionType,
        Difficulty difficulty, UUID skillId, String skillName, int displayOrder, int points,
        List<PlacementAttemptResultOptionView> options, List<UUID> selectedOptionIds, List<UUID> correctOptionIds,
        boolean correct, String explanation) {
}
