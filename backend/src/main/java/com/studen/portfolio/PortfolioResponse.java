package com.studen.portfolio;

import com.studen.skill.SkillLevel;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PortfolioResponse(
        UUID id,
        String headline,
        String bio,
        String experienceSummary,
        String responseTime,
        String location,
        boolean available,
        String publicSlug,
        String profileUrl,
        String coverImageUrl,
        List<PortfolioSkillResponse> skills,
        Set<AvailabilityOption> availableFor,
        Instant createdAt,
        Instant updatedAt) {

    public static PortfolioResponse from(
            StudentPortfolio portfolio, String publicProfileBaseUrl, Map<UUID, SkillLevel> levelsBySkillId) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getHeadline(),
                portfolio.getBio(),
                portfolio.getExperienceSummary(),
                portfolio.getResponseTime(),
                portfolio.getLocation(),
                portfolio.isAvailable(),
                portfolio.getPublicSlug(),
                publicProfileBaseUrl + "/" + portfolio.getPublicSlug(),
                portfolio.getCoverImageUrl(),
                portfolio.getSkills().stream()
                        .map(skill -> PortfolioSkillResponse.from(
                                skill, levelsBySkillId.getOrDefault(skill.getId(), SkillLevel.BEGINNER)))
                        .toList(),
                new LinkedHashSet<>(portfolio.getAvailableFor()),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }
}
