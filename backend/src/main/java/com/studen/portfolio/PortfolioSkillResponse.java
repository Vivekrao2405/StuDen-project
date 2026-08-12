package com.studen.portfolio;

import com.studen.skill.IconType;
import com.studen.skill.Skill;
import com.studen.skill.SkillLevel;
import java.util.UUID;

public record PortfolioSkillResponse(
        UUID id, String name, String category, String iconSlug, IconType iconType, SkillLevel level) {

    public static PortfolioSkillResponse from(Skill skill, SkillLevel level) {
        return new PortfolioSkillResponse(
                skill.getId(), skill.getName(), skill.getCategory(), skill.getIconSlug(), skill.getIconType(), level);
    }
}
