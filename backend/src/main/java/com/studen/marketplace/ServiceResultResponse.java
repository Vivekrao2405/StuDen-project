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
        String providerHeadline,
        String providerSlug,
        String providerProfileImageUrl,
        List<SkillResponse> skills,
        Integer priceAmount,
        ServiceCurrency currency,
        Integer deliveryDays,
        boolean available,
        String coverImageUrl) implements MarketplaceResultResponse {

    // `media` is passed explicitly (batch-fetched by the caller across every candidate in one
    // query) rather than read through listing.getMedia() — same N+1-avoidance reasoning as the
    // showcase module's ProjectResponse/PublicProjectSummaryResponse.
    public static ServiceResultResponse from(ServiceListing listing, List<ServiceMedia> media) {
        StudentPortfolio portfolio = listing.getPortfolio();
        return new ServiceResultResponse(
                "SERVICE",
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory(),
                listing.getLocation(),
                portfolio.getUser().getFullName(),
                portfolio.getHeadline(),
                portfolio.getPublicSlug(),
                portfolio.getUser().getProfileImageUrl(),
                listing.getSkills().stream().map(SkillResponse::from).toList(),
                listing.getPriceAmount(),
                listing.getCurrency(),
                listing.getDeliveryDays(),
                listing.isAvailable(),
                ServiceCoverMediaResolver.resolve(media));
    }
}
