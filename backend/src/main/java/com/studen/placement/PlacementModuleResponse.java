package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlacementModuleResponse(
        UUID id,
        UUID seriesId,
        String name,
        String description,
        int displayOrder,
        Integer requiredItemCount,
        List<PlacementModuleItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementModuleResponse from(PlacementModule module, List<PlacementModuleItem> items) {
        return new PlacementModuleResponse(module.getId(), module.getSeries().getId(), module.getName(),
                module.getDescription(), module.getDisplayOrder(), module.getRequiredItemCount(),
                items.stream().map(PlacementModuleItemResponse::from).toList(),
                module.getCreatedAt(), module.getUpdatedAt());
    }
}
