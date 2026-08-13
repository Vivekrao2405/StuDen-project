package com.studen.marketplace;

import com.studen.skill.SkillResponse;
import java.util.List;
import java.util.UUID;

/** Full service detail for the public service page — the owning student is identified only by
 * their public slug/name/photo, never their internal user/portfolio id. Mirrors
 * {@code com.studen.showcase.PublicProjectDetailResponse}'s privacy convention exactly. */
public record PublicServiceDetailResponse(
        UUID id,
        String title,
        String description,
        MarketplaceCategory category,
        String location,
        boolean available,
        Integer priceAmount,
        ServiceCurrency currency,
        Integer deliveryDays,
        List<SkillResponse> skills,
        List<String> whatYoullReceive,
        List<ServiceMediaResponse> media,
        List<ServiceLinkResponse> links,
        List<ServiceProjectSummaryResponse> linkedProjects,
        String coverImageUrl,
        String providerName,
        String providerHeadline,
        String providerProfileImageUrl,
        String providerSlug) {
}
