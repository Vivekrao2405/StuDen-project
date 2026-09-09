package com.studen.placement;

import com.studen.practical.PracticalType;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import java.util.Set;
import java.util.UUID;

// The configured item as an admin sees it while building an assessment. Deliberately carries no
// options/test cases/expected solution: this is assessment configuration, not a student-facing
// paper. Exactly one of questionId/practicalAssessmentId is non-null, discriminated by itemType.
// `skillMappedToRole` surfaces (never silently hides) an item whose skill isn't among the target
// role's configured RoleSkill set, per the spec's "prevent or clearly warn" requirement — it does
// not block adding the item, since an admin may deliberately want an unmapped skill.
public record PlacementAssessmentQuestionResponse(
        UUID id,
        ModuleItemType itemType,
        UUID questionId,
        String questionTextPreview,
        QuestionType questionType,
        UUID practicalAssessmentId,
        String practicalAssessmentTitle,
        PracticalType practicalType,
        Difficulty difficulty,
        UUID skillId,
        String skillName,
        boolean skillMappedToRole,
        int displayOrder,
        int points) {

    private static final int PREVIEW_LENGTH = 140;

    public static PlacementAssessmentQuestionResponse from(PlacementAssessmentQuestion link, Set<UUID> roleSkillIds) {
        var skill = link.resolveSkill();
        boolean mapped = roleSkillIds.contains(skill.getId());
        if (link.getItemType() == ModuleItemType.QUESTION) {
            String text = link.getQuestion().getQuestionText();
            String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
            return new PlacementAssessmentQuestionResponse(link.getId(), ModuleItemType.QUESTION,
                    link.getQuestion().getId(), preview, link.getQuestion().getQuestionType(), null, null, null,
                    link.getQuestion().getDifficulty(), skill.getId(), skill.getName(), mapped, link.getDisplayOrder(),
                    link.getPoints());
        }
        var practical = link.getPracticalAssessment();
        return new PlacementAssessmentQuestionResponse(link.getId(), ModuleItemType.PRACTICAL_ASSESSMENT, null, null,
                null, practical.getId(), practical.getTitle(), practical.getPracticalType(), practical.getDifficulty(),
                skill.getId(), skill.getName(), mapped, link.getDisplayOrder(), link.getPoints());
    }
}
