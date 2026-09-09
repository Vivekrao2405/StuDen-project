package com.studen.placement;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record PlacementModuleItemAnswerRequest(@NotEmpty(message = "At least one option must be selected") List<UUID> selectedOptionIds) {
}
