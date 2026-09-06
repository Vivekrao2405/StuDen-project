package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.UUID;

// The configured question as an admin sees it while building an assessment. Deliberately carries
// no options and no correct answer: this is assessment configuration, not a student-facing paper.
public record PlacementAssessmentQuestionResponse(
        UUID id,
        UUID questionId,
        String questionTextPreview,
        QuestionType questionType,
        Difficulty difficulty,
        UUID skillId,
        String skillName,
        int displayOrder,
        int points) {

    private static final int PREVIEW_LENGTH = 140;

    public static PlacementAssessmentQuestionResponse from(PlacementAssessmentQuestion link) {
        String text = link.getQuestion().getQuestionText();
        String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
        return new PlacementAssessmentQuestionResponse(
                link.getId(),
                link.getQuestion().getId(),
                preview,
                link.getQuestion().getQuestionType(),
                link.getQuestion().getDifficulty(),
                link.getQuestion().getSkill().getId(),
                link.getQuestion().getSkill().getName(),
                link.getDisplayOrder(),
                link.getPoints());
    }
}
