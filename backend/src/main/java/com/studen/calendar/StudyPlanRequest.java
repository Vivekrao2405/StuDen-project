package com.studen.calendar;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

// Pure-computation preview input (section 14) — never writes anything. `startDate` is the first
// date to consider (the frontend's "today" in the student's local calendar); the plan covers the
// next 7 days from there, one suggested session on each date whose day-of-week is in
// `availableDays`, matching the spec's own one-week Monday-Friday example.
public record StudyPlanRequest(

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotEmpty(message = "At least one available day is required")
        Set<DayOfWeek> availableDays,

        @NotNull(message = "Daily study duration is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        Integer durationMinutesPerDay) {
}
