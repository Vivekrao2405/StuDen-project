package com.studen.skill;

import java.time.Instant;
import java.util.UUID;

// Admin list/detail view of a catalog skill. The public SkillResponse (search results) stays
// unchanged on purpose, since widening it here would touch every existing caller.
public record AdminSkillResponse(
        UUID id,
        String name,
        String category,
        String iconSlug,
        IconType iconType,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminSkillResponse from(Skill skill) {
        return new AdminSkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getIconSlug(),
                skill.getIconType(), skill.getCreatedAt(), skill.getUpdatedAt());
    }
}
