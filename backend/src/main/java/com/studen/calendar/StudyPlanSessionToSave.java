package com.studen.calendar;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

// The (possibly student-edited) preview, now with a concrete clock time attached per day —
// resourceId is null only for a kept "Practice / Revision" slot.
public record StudyPlanSessionToSave(
        UUID resourceId,
        String topic,

        @NotNull(message = "Start time is required")
        Instant scheduledStart,

        @NotNull(message = "Duration is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        Integer durationMinutes) {
}
