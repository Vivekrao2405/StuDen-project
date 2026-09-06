package com.studen.placement;

import java.time.Instant;
import java.util.UUID;

// List-row view. skillCount is passed in by the service from a counted query rather than read off
// the lazy roleSkills collection, so listing roles never triggers one extra query per row.
public record PlacementRoleResponse(
        UUID id,
        String name,
        String description,
        PlacementCatalogStatus status,
        int displayOrder,
        int skillCount,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementRoleResponse from(PlacementRole role, int skillCount) {
        return new PlacementRoleResponse(role.getId(), role.getName(), role.getDescription(), role.getStatus(),
                role.getDisplayOrder(), skillCount, role.getCreatedAt(), role.getUpdatedAt());
    }
}
