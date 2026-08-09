package com.studen.education;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EducationRequest(

        @NotBlank(message = "Degree is required")
        @Size(max = 255, message = "Degree must be at most 255 characters")
        String degree,

        @Size(max = 255, message = "Field of study must be at most 255 characters")
        String fieldOfStudy,

        @NotBlank(message = "Institution is required")
        @Size(max = 255, message = "Institution must be at most 255 characters")
        String institution,

        @NotNull(message = "Start year is required")
        @Min(value = 1950, message = "Start year must be 1950 or later")
        @Max(value = 2100, message = "Start year must be 2100 or earlier")
        Integer startYear,

        @Min(value = 1950, message = "End year must be 1950 or later")
        @Max(value = 2100, message = "End year must be 2100 or earlier")
        Integer endYear,

        boolean current) {
}
