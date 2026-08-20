package com.studen.communication;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-recipient failure detail for admin diagnosis of a campaign's FAILED rows. {@code
 * errorMessage} is the real provider error captured verbatim at send time (see
 * CampaignDeliveryTransactions#recordResult) — never fabricated or re-derived. {@code
 * recipientEmail} is the same audit-display snapshot already stored on {@link
 * CommunicationRecipient} for exactly this purpose. Gated the same as every other endpoint on
 * AdminCommunicationCampaignController (ADMIN only) and never includes any credential/secret.
 */
public record RecipientFailureResponse(
        UUID recipientId,
        String recipientEmail,
        RecipientChannel channel,
        String errorMessage,
        Instant updatedAt) {

    public static RecipientFailureResponse from(CommunicationRecipient r) {
        return new RecipientFailureResponse(r.getId(), r.getRecipientEmail(), r.getChannel(), r.getErrorMessage(),
                r.getUpdatedAt());
    }
}
