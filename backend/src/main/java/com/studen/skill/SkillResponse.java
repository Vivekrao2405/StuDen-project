package com.studen.skill;

import java.util.UUID;

public record SkillResponse(UUID id, String name, String category, String iconSlug, IconType iconType) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getIconSlug(),
                skill.getIconType());
    }
}
