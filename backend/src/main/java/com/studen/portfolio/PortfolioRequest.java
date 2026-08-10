package com.studen.portfolio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PortfolioRequest(

        @NotBlank(message = "Headline is required")
        @Size(max = 255, message = "Headline must be at most 255 characters")
        String headline,

        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio,

        @Size(max = 2000, message = "Experience summary must be at most 2000 characters")
        String experienceSummary,

        @DecimalMin(value = "0.0", message = "Hourly rate must not be negative")
        @Digits(integer = 8, fraction = 2, message = "Hourly rate must be a valid amount")
        BigDecimal hourlyRate,

        @Size(max = 100, message = "Response time must be at most 100 characters")
        String responseTime,

        @Size(max = 255, message = "Location must be at most 255 characters")
        String location,

        Boolean available) {
}
