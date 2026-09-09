package com.studen.placement;

import java.util.UUID;

// "Continue Preparation" (spec §12): the next incomplete required item, in module/item display
// order. allComplete is true once every module in the series is COMPLETED — every other field is
// then null, since there is nothing left to continue to.
public record PlacementContinueResponse(
        UUID seriesId,
        boolean allComplete,
        UUID moduleId,
        String moduleName,
        UUID itemId,
        ModuleItemType itemType,
        UUID targetId,
        String itemTitle) {

    public static PlacementContinueResponse complete(UUID seriesId) {
        return new PlacementContinueResponse(seriesId, true, null, null, null, null, null, null);
    }

    public static PlacementContinueResponse of(UUID seriesId, PlacementModule module, PlacementModuleItemResponse item) {
        return new PlacementContinueResponse(seriesId, false, module.getId(), module.getName(), item.id(),
                item.itemType(), item.targetId(), item.title());
    }
}
