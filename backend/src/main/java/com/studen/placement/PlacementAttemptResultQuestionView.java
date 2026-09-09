package com.studen.placement;

import com.studen.practical.PracticalAttemptStatus;
import com.studen.practical.PracticalType;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.List;
import java.util.UUID;

// Post-submission review — correct answers/explanation are safe to expose for a QUESTION slot
// because the attempt is now permanently locked. A PRACTICAL_ASSESSMENT slot never carries hidden
// test cases or an expected solution here; `practicalScorePercentage` is read straight off the
// linked (now terminal) PracticalAttempt, never recomputed.
public record PlacementAttemptResultQuestionView(UUID id, ModuleItemType itemType, String questionText,
        QuestionType questionType, Difficulty difficulty, List<PlacementAttemptResultOptionView> options,
        List<UUID> selectedOptionIds, List<UUID> correctOptionIds, boolean correct, String explanation,
        UUID practicalAssessmentId, String practicalAssessmentTitle, PracticalType practicalType,
        UUID practicalAttemptId, PracticalAttemptStatus practicalAttemptStatus, Integer practicalScorePercentage,
        UUID skillId, String skillName, int displayOrder, int points) {
}
