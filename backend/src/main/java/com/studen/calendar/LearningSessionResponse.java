package com.studen.calendar;

import com.studen.resource.ResourceCardResponse;
import java.time.Instant;
import java.util.UUID;

public record LearningSessionResponse(
        UUID id,
        String topic,
        ResourceCardResponse resource,
        Instant scheduledStart,
        Integer durationMinutes,
        LearningSessionStatus status,
        Instant completedAt,
        LearningSessionCategory category) {
}
