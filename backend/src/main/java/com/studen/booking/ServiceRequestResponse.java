package com.studen.booking;

import com.studen.marketplace.ServiceCurrency;
import com.studen.portfolio.StudentPortfolio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Privacy convention matches PublicServiceDetailResponse/PublicProjectDetailResponse: the
// counterpart is identified only by name/headline/photo/slug, never their internal user id, and
// email/phone are never included at all.
public record ServiceRequestResponse(
        UUID id,
        UUID serviceId,
        String serviceTitle,
        Integer servicePriceAmount,
        ServiceCurrency serviceCurrency,
        ServiceRequestStatus status,
        String description,
        LocalDate requestedDeliveryDate,
        Integer proposedBudget,
        List<ServiceRequestLinkResponse> links,
        Instant acceptedAt,
        Instant rejectedAt,
        String rejectionReason,
        String requesterName,
        String requesterProfileImageUrl,
        String requesterSlug,
        String providerName,
        String providerHeadline,
        String providerProfileImageUrl,
        String providerSlug,
        Instant createdAt,
        Instant updatedAt) {

    // requesterPortfolio/providerPortfolio are nullable — not every user has created a portfolio
    // yet (a brand-new requester in particular), so slug/headline degrade to null rather than the
    // whole response failing.
    public static ServiceRequestResponse from(ServiceRequest request, StudentPortfolio requesterPortfolio,
            StudentPortfolio providerPortfolio) {
        return new ServiceRequestResponse(
                request.getId(),
                request.getService() == null ? null : request.getService().getId(),
                request.getServiceTitleSnapshot(),
                request.getServicePriceSnapshot(),
                request.getServiceCurrencySnapshot(),
                request.getStatus(),
                request.getDescription(),
                request.getRequestedDeliveryDate(),
                request.getProposedBudget(),
                // .stream().toList() forces this lazy @ElementCollection to materialize while the
                // transaction/session is still open — same reasoning as ServiceResponse.from.
                request.getLinks().stream().map(ServiceRequestLinkResponse::from).toList(),
                request.getAcceptedAt(),
                request.getRejectedAt(),
                request.getRejectionReason(),
                request.getRequester().getFullName(),
                request.getRequester().getProfileImageUrl(),
                requesterPortfolio == null ? null : requesterPortfolio.getPublicSlug(),
                request.getProvider().getFullName(),
                providerPortfolio == null ? null : providerPortfolio.getHeadline(),
                request.getProvider().getProfileImageUrl(),
                providerPortfolio == null ? null : providerPortfolio.getPublicSlug(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
