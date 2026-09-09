package com.studen.placement;

import com.studen.practical.PracticalAttemptStatus;
import com.studen.practical.PracticalType;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.List;
import java.util.UUID;

// In-progress slot view — either a QUESTION (MCQ fields populated, practical fields null) or a
// PRACTICAL_ASSESSMENT slot (practical fields populated, options/selectedOptionIds empty — the
// student answers it entirely through the existing practical-attempt taking UI at
// practicalAttemptId, never here). Structurally cannot carry isCorrect/explanation/hidden test
// cases either way.
public record PlacementAttemptQuestionView(UUID id, ModuleItemType itemType, String questionText,
        QuestionType questionType, Difficulty difficulty, List<PlacementAttemptOptionView> options,
        List<UUID> selectedOptionIds, UUID practicalAssessmentId, String practicalAssessmentTitle,
        PracticalType practicalType, UUID practicalAttemptId, PracticalAttemptStatus practicalAttemptStatus,
        UUID skillId, String skillName, int displayOrder, int points) {
}
