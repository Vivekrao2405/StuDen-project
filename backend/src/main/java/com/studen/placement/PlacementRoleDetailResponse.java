package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Role plus its full required-skill mapping: the answer to "what skills does this role require?",
// which is the query the rest of the placement system is built on.
public record PlacementRoleDetailResponse(
        UUID id,
        String name,
        String description,
        PlacementCatalogStatus status,
        int displayOrder,
        List<RoleSkillResponse> skills,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementRoleDetailResponse from(PlacementRole role, List<RoleSkill> roleSkills) {
        // Materialized eagerly: open-in-view is false app-wide, so a lazy collection handed
        // straight to Jackson would blow up once the transaction closes.
        List<RoleSkillResponse> skills = roleSkills.stream().map(RoleSkillResponse::from).toList();
        return new PlacementRoleDetailResponse(role.getId(), role.getName(), role.getDescription(), role.getStatus(),
                role.getDisplayOrder(), skills, role.getCreatedAt(), role.getUpdatedAt());
    }
}
