package com.studen.resource;

import com.studen.skill.SkillResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Admin view of one resource's full Placement skill mapping — the mandatory primary skill (set on
// the resource itself, edited through the regular resource form) plus the admin-managed additional
// skills (resource_skills, Phase 4). Both count as "mapped" for placement recommendation matching.
public record ResourceSkillMappingResponse(
        UUID resourceId,
        String resourceTitle,
        UUID primarySkillId,
        String primarySkillName,
        List<SkillResponse> additionalSkills) {

    public static ResourceSkillMappingResponse from(Resource entity) {
        List<SkillResponse> additional = entity.getAdditionalSkills().stream()
                .map(s -> new SkillResponse(s.getId(), s.getName(), s.getCategory(), s.getIconSlug(), s.getIconType()))
                .sorted(Comparator.comparing(SkillResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new ResourceSkillMappingResponse(entity.getId(), entity.getTitle(), entity.getSkill().getId(),
                entity.getSkill().getName(), additional);
    }
}
