package com.studen.marketplace;

import com.studen.portfolio.StudentPortfolio;
import com.studen.skill.SkillResponse;
import java.util.List;
import java.util.UUID;

/**
 * A service-listing marketplace result. The listing's own id is exposed (there's no public-slug
 * concept for services yet — it's an opaque catalog id, not sensitive); the provider is
 * identified by their public slug, never their internal user/portfolio id.
 */
public record ServiceResultResponse(
        String type,
        UUID id,
        String title,
        String description,
        MarketplaceCategory category,
        String location,
        String providerName,
        String providerSlug,
        String providerProfileImageUrl,
        List<SkillResponse> skills) implements MarketplaceResultResponse {

    public static ServiceResultResponse from(ServiceListing listing) {
        StudentPortfolio portfolio = listing.getPortfolio();
        return new ServiceResultResponse(
                "SERVICE",
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory(),
                listing.getLocation(),
                portfolio.getUser().getFullName(),
                portfolio.getPublicSlug(),
                portfolio.getUser().getProfileImageUrl(),
                listing.getSkills().stream().map(SkillResponse::from).toList());
    }
}
