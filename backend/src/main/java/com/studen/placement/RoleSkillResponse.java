package com.studen.placement;

import com.studen.assessment.AssessmentLevel;
import com.studen.skill.IconType;
import java.util.UUID;

// One required skill for a role, flattened with the catalog skill fields the UI needs so a caller
// never has to make a second request per skill.
public record RoleSkillResponse(
        UUID id,
        UUID skillId,
        String skillName,
        String skillCategory,
        String skillIconSlug,
        IconType skillIconType,
        int weight,
        AssessmentLevel requiredProficiency,
        int priority) {

    public static RoleSkillResponse from(RoleSkill roleSkill) {
        return new RoleSkillResponse(
                roleSkill.getId(),
                roleSkill.getSkill().getId(),
                roleSkill.getSkill().getName(),
                roleSkill.getSkill().getCategory(),
                roleSkill.getSkill().getIconSlug(),
                roleSkill.getSkill().getIconType(),
                roleSkill.getWeight(),
                roleSkill.getRequiredProficiency(),
                roleSkill.getPriority());
    }
}
