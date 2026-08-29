package com.studen.calendar;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ScheduleSessionRequest(

        // Nullable only for a resource-less "Practice / Revision" session (see study-plan save) —
        // every other schedule action (from the roadmap or a resource detail page) supplies a real
        // resource, enforced by CalendarService requiring at least one of resourceId/topic.
        UUID resourceId,

        // The roadmap always knows the topic it's scheduling; a session can also be created
        // directly from a resource's own detail page with no specific topic in mind.
        String topic,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        Instant scheduledStart,

        @NotNull(message = "Duration is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        Integer durationMinutes,

        // Null defaults to LEARNING in CalendarService -- optional so every existing caller (the
        // Roadmap's "Schedule" action) keeps working unchanged.
        LearningSessionCategory category) {
}
