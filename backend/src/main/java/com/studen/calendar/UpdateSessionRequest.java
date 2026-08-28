package com.studen.calendar;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

// Edit only ever touches when/how long — resource/topic are fixed at scheduling time; to change
// what's being studied, delete and re-schedule (mirrors this app's existing "replace, don't patch
// piecemeal" convention for structural changes elsewhere, e.g. AdminPracticalAssessmentService).
public record UpdateSessionRequest(

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        Instant scheduledStart,

        @NotNull(message = "Duration is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        Integer durationMinutes) {
}
