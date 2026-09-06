package com.studen.placement;

import java.time.Instant;
import java.util.UUID;

public record PlacementCompanyResponse(
        UUID id,
        String name,
        CompanyType companyType,
        String description,
        String logoUrl,
        PlacementCatalogStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PlacementCompanyResponse from(PlacementCompany company) {
        return new PlacementCompanyResponse(company.getId(), company.getName(), company.getCompanyType(),
                company.getDescription(), company.getLogoUrl(), company.getStatus(), company.getCreatedAt(),
                company.getUpdatedAt());
    }
}
