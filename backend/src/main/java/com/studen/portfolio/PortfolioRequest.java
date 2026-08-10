package com.studen.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record PortfolioRequest(

        @NotBlank(message = "Please tell us what you do")
        @Size(max = 255, message = "This must be at most 255 characters")
        String headline,

        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio,

        @Size(max = 2000, message = "Experience summary must be at most 2000 characters")
        String experienceSummary,

        @Size(max = 100, message = "Response time must be at most 100 characters")
        String responseTime,

        @Size(max = 255, message = "Location must be at most 255 characters")
        String location,

        Boolean available,

        Set<UUID> skillIds,

        Set<AvailabilityOption> availableFor) {
}
