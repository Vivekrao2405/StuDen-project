package com.studen.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(
        UUID id,
        String headline,
        String bio,
        String experienceSummary,
        BigDecimal hourlyRate,
        String responseTime,
        String location,
        boolean available,
        String publicSlug,
        String profileUrl,
        String coverImageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static PortfolioResponse from(StudentPortfolio portfolio, String publicProfileBaseUrl) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getHeadline(),
                portfolio.getBio(),
                portfolio.getExperienceSummary(),
                portfolio.getHourlyRate(),
                portfolio.getResponseTime(),
                portfolio.getLocation(),
                portfolio.isAvailable(),
                portfolio.getPublicSlug(),
                publicProfileBaseUrl + "/" + portfolio.getPublicSlug(),
                portfolio.getCoverImageUrl(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }
}
