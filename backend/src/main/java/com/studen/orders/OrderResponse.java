package com.studen.orders;

import com.studen.booking.ServiceRequest;
import com.studen.marketplace.ServiceCurrency;
import com.studen.portfolio.StudentPortfolio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// One shape for both the list and detail views (same reuse ServiceRequestResponse already does).
// Service title/price/currency/requirements/budget/delivery-date are all read through
// serviceRequest rather than duplicated on WorkOrder itself — see WorkOrder's class comment.
// requestAcceptedAt is kept distinct from this order's own createdAt so the client-side timeline
// can show an honest "Request accepted" entry separate from "Work started", even though in
// practice they land within the same request cycle.
public record OrderResponse(
        UUID id,
        UUID serviceRequestId,
        String serviceTitle,
        Integer servicePriceAmount,
        ServiceCurrency serviceCurrency,
        String requirements,
        Integer proposedBudget,
        LocalDate requestedDeliveryDate,
        OrderStatus status,
        Instant requestAcceptedAt,
        Instant createdAt,
        Instant submittedAt,
        String submissionDescription,
        String submissionLink,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        String requesterName,
        String requesterProfileImageUrl,
        String requesterSlug,
        String providerName,
        String providerHeadline,
        String providerProfileImageUrl,
        String providerSlug) {

    public static OrderResponse from(WorkOrder order, StudentPortfolio requesterPortfolio, StudentPortfolio providerPortfolio) {
        ServiceRequest request = order.getServiceRequest();
        return new OrderResponse(
                order.getId(),
                request.getId(),
                request.getServiceTitleSnapshot(),
                request.getServicePriceSnapshot(),
                request.getServiceCurrencySnapshot(),
                request.getDescription(),
                request.getProposedBudget(),
                request.getRequestedDeliveryDate(),
                order.getStatus(),
                request.getAcceptedAt(),
                order.getCreatedAt(),
                order.getSubmittedAt(),
                order.getSubmissionDescription(),
                order.getSubmissionLink(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                order.getRequester().getFullName(),
                order.getRequester().getProfileImageUrl(),
                requesterPortfolio == null ? null : requesterPortfolio.getPublicSlug(),
                order.getProvider().getFullName(),
                providerPortfolio == null ? null : providerPortfolio.getHeadline(),
                order.getProvider().getProfileImageUrl(),
                providerPortfolio == null ? null : providerPortfolio.getPublicSlug());
    }
}
