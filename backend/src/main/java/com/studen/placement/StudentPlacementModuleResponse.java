package com.studen.placement;

import java.util.List;
import java.util.UUID;

// A module's student-facing roll-up. progressPercentage/status are derived entirely from the
// caller's own item progress every time this is built — never a stored, cacheable summary (same
// "never trust a stored summary" posture PlacementAttemptService/PlacementReadinessService already
// follow for readiness scoring).
public record StudentPlacementModuleResponse(
        UUID id,
        String name,
        String description,
        int displayOrder,
        Integer requiredItemCount,
        int totalItemCount,
        int completedItemCount,
        int progressPercentage,
        PlacementProgressStatus status,
        List<StudentPlacementModuleItemResponse> items) {
}
