package com.studen.communication;

import java.util.UUID;

/**
 * Shared shape for both creating a DRAFT campaign and replacing one while it's still a DRAFT
 * (CommunicationService rejects any update once a campaign has left DRAFT). {@code filterJson} is
 * exactly what {@link com.studen.communication.audience.AudienceFilterParser} expects — the
 * builder UI's filter tree, stringified.
 */
public record CampaignRequest(
        String name,
        CommunicationCategory category,
        boolean marketing,
        String filterJson,
        UUID templateId,
        UUID segmentId,
        boolean sendEmail,
        boolean sendPush,
        boolean sendInapp,
        String emailSubject,
        String emailBodyHtml,
        String pushTitle,
        String pushBody,
        String inappTitle,
        String inappBody,
        String ctaText,
        String ctaUrl) {
}
