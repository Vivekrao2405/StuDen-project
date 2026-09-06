package com.studen.placement;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

// Shared by module reordering and module-item reordering: the full list of ids in their new
// order. Reordering assigns display_order by list position, which is why display_order carries no
// unique constraint (see PlacementModule).
public record ReorderRequest(

        @NotEmpty(message = "Ordered ids are required")
        List<UUID> orderedIds) {
}
