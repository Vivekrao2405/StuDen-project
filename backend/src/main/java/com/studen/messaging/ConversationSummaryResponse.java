package com.studen.messaging;

import com.studen.marketplace.ServiceCurrency;
import java.time.Instant;
import java.util.UUID;

// The conversation-list row shape — same participant/service fields as ConversationResponse plus
// a lightweight preview. Deliberately never carries the full message history (see
// ConversationService.listConversations).
public record ConversationSummaryResponse(
        UUID id,
        UUID serviceRequestId,
        String serviceTitle,
        Integer servicePriceAmount,
        ServiceCurrency serviceCurrency,
        String otherParticipantName,
        String otherParticipantProfileImageUrl,
        String otherParticipantSlug,
        String otherParticipantHeadline,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unreadCount,
        Instant createdAt) {
}
