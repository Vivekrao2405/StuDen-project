package com.studen.assessment;

import com.studen.skill.IconType;
import com.studen.skill.Skill;
import java.util.UUID;

// Only skills with >=1 PUBLISHED question are ever listed at all — a skill with zero content has
// nothing to promote. `assessable` distinguishes "enough questions to actually start" (spec §5)
// from "some questions exist but not enough yet" (rendered as "Assessment coming soon", spec §35).
public record AssessableSkillResponse(UUID skillId, String name, String category, String iconSlug, IconType iconType,
        int publishedQuestionCount, int requiredQuestionCount, boolean assessable) {

    public static AssessableSkillResponse of(Skill skill, int publishedQuestionCount, int requiredQuestionCount) {
        return new AssessableSkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getIconSlug(),
                skill.getIconType(), publishedQuestionCount, requiredQuestionCount,
                publishedQuestionCount >= requiredQuestionCount);
    }
}
