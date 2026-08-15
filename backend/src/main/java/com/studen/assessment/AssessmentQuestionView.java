package com.studen.assessment;

import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.List;
import java.util.UUID;

// In-progress question view — structurally cannot carry isCorrect/explanation/correctOptionId
// (spec §15). `selectedOptionIds` is the student's own previously-saved answer for this question,
// if any, so navigating away and back (or refreshing) doesn't lose their in-progress state.
public record AssessmentQuestionView(UUID id, String questionText, QuestionType questionType, Difficulty difficulty,
        int displayOrder, int points, List<AssessmentOptionView> options, List<UUID> selectedOptionIds) {
}
