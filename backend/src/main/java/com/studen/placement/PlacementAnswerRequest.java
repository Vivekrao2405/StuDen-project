package com.studen.placement;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record PlacementAnswerRequest(@NotEmpty List<UUID> selectedOptionIds) {
}
