package com.studen.placement;

import java.util.UUID;

// The student-facing view of a module item: the same (targetId, title) flattening
// PlacementModuleItemResponse already does for admin, plus the caller's own progress on it.
public record StudentPlacementModuleItemResponse(
        UUID id,
        ModuleItemType itemType,
        UUID targetId,
        String title,
        int displayOrder,
        boolean required,
        PlacementProgressStatus status) {

    public static StudentPlacementModuleItemResponse from(PlacementModuleItem item, PlacementProgressStatus status) {
        PlacementModuleItemResponse base = PlacementModuleItemResponse.from(item);
        return new StudentPlacementModuleItemResponse(base.id(), base.itemType(), base.targetId(), base.title(),
                base.displayOrder(), base.required(), status);
    }
}
