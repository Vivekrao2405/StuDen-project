package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.List;
import java.util.UUID;

// In-progress question view — structurally cannot carry isCorrect/explanation/correctOptionId.
// `selectedOptionIds` is the student's own previously-saved answer, if any, so navigating away and
// back (or refreshing) doesn't lose in-progress state.
public record PlacementAttemptQuestionView(UUID id, String questionText, QuestionType questionType,
        Difficulty difficulty, UUID skillId, String skillName, int displayOrder, int points,
        List<PlacementAttemptOptionView> options, List<UUID> selectedOptionIds) {
}
