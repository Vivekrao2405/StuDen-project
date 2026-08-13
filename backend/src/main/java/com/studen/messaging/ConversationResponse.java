package com.studen.messaging;

import com.studen.marketplace.ServiceCurrency;
import java.time.Instant;
import java.util.UUID;

// Privacy convention matches ServiceRequestResponse/PublicServiceDetailResponse: the other
// participant is identified only by name/photo/slug/headline, never their internal user id, and
// email/phone are never included at all.
public record ConversationResponse(
        UUID id,
        UUID serviceRequestId,
        String serviceTitle,
        Integer servicePriceAmount,
        ServiceCurrency serviceCurrency,
        String otherParticipantName,
        String otherParticipantProfileImageUrl,
        String otherParticipantSlug,
        String otherParticipantHeadline,
        Instant createdAt) {
}
