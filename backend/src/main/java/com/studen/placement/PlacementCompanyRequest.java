package com.studen.placement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlacementCompanyRequest(

        @NotBlank(message = "Company name is required")
        @Size(max = 150, message = "Company name must be at most 150 characters")
        String name,

        @NotNull(message = "Company type is required")
        CompanyType companyType,

        String description,

        String logoUrl) {
}
