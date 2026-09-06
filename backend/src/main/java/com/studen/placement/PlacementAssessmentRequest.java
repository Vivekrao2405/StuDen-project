package com.studen.placement;

import com.studen.questionbank.Difficulty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// Assessment configuration only. Questions are attached through the dedicated questions endpoints,
// and status changes through publish/archive, mirroring how the Question Bank and practical
// assessments already split those concerns.
public record PlacementAssessmentRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        String description,

        @NotNull(message = "Role is required")
        UUID roleId,

        @NotNull(message = "Difficulty is required")
        Difficulty difficulty,

        @Min(value = 1, message = "Duration must be at least 1 minute")
        Integer durationMinutes,

        @Min(value = 0, message = "Passing score cannot be negative")
        @Max(value = 100, message = "Passing score cannot exceed 100")
        Integer passingScore) {
}
