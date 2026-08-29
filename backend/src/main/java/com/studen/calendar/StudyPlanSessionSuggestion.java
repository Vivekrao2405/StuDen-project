package com.studen.calendar;

import com.studen.resource.ResourceCardResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

// One proposed (not yet saved) day of the generated study plan. `resource`/`skillId`/`skillName`
// are null only for a "Practice / Revision" slot — assigned once every not-yet-completed roadmap
// topic has already been placed on an earlier day, matching the spec's own Friday example. Never a
// fabricated resource: a revision slot is honestly resource-less, not a repeat of an earlier day's
// real resource.
public record StudyPlanSessionSuggestion(
        LocalDate date,
        DayOfWeek dayOfWeek,
        UUID skillId,
        String skillName,
        String topic,
        ResourceCardResponse resource,
        Integer durationMinutes,
        LearningSessionCategory category) {
}
