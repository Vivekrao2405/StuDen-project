package com.studen.calendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SaveStudyPlanRequest(

        @NotEmpty(message = "At least one session is required")
        @Valid
        List<StudyPlanSessionToSave> sessions) {
}
