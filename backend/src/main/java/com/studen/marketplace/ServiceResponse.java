package com.studen.marketplace;

import com.studen.skill.SkillResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        String title,
        String description,
        MarketplaceCategory category,
        String location,
        ServiceStatus status,
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
        Instant createdAt,
        Instant updatedAt) {

    // `media` and `linkedProjects` are passed explicitly (batch-fetched fresh by the caller)
    // rather than read through service.getMedia()/getLinkedProjects() — same reasoning as
    // ProjectResponse.from: avoids a stale/uninitialized lazy collection right after a
    // media/link write, and lets callers batch-fetch across many services in one query.
    public static ServiceResponse from(ServiceListing service, List<ServiceMedia> media,
            List<ServiceProjectSummaryResponse> linkedProjects) {
        return new ServiceResponse(
                service.getId(),
                service.getTitle(),
                service.getDescription(),
                service.getCategory(),
                service.getLocation(),
                service.getStatus(),
                service.isAvailable(),
                service.getPriceAmount(),
                service.getCurrency(),
                service.getDeliveryDays(),
                service.getSkills().stream().map(SkillResponse::from).toList(),
                // .stream().toList() forces this lazy @ElementCollection to materialize into a
                // plain list while the transaction/session is still open — passing the raw
                // Hibernate-backed List reference through would defer iteration to Jackson
                // serialization time, which runs after the session closes (open-in-view: false)
                // and throws LazyInitializationException.
                service.getWhatYoullReceive().stream().toList(),
                media.stream().map(ServiceMediaResponse::from).toList(),
                service.getLinks().stream().map(ServiceLinkResponse::from).toList(),
                linkedProjects,
                ServiceCoverMediaResolver.resolve(media),
                service.getCreatedAt(),
                service.getUpdatedAt());
    }
}
