package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlacementAnswerResponse(UUID attemptQuestionId, List<UUID> selectedOptionIds, Instant answeredAt) {
}
