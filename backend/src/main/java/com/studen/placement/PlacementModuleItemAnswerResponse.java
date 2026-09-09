package com.studen.placement;

import java.util.List;
import java.util.UUID;

public record PlacementModuleItemAnswerResponse(
        UUID itemId,
        boolean correct,
        List<UUID> correctOptionIds,
        String explanation,
        PlacementProgressStatus status) {
}
