package com.studen.assessment;

import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.List;
import java.util.UUID;

// Post-submission only (spec §31/§32) — correct answers and explanations are safe to expose here
// because the assessment is now permanently locked (no further answer mutation is possible).
public record AssessmentResultQuestionView(UUID id, String questionText, QuestionType questionType,
        Difficulty difficulty, int displayOrder, int points, List<AssessmentResultOptionView> options,
        List<UUID> selectedOptionIds, List<UUID> correctOptionIds, boolean correct, String explanation) {
}
