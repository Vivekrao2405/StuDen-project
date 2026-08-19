package com.studen.communication;

import java.time.Instant;
import java.util.UUID;

public record CampaignSummaryResponse(
        UUID id,
        String name,
        CommunicationCategory category,
        CampaignStatus status,
        boolean marketing,
        boolean sendEmail,
        boolean sendPush,
        boolean sendInapp,
        Integer resolvedRecipientCount,
        String createdByName,
        Instant scheduledAt,
        Instant sentAt,
        Instant createdAt) {

    public static CampaignSummaryResponse from(CommunicationCampaign c) {
        return new CampaignSummaryResponse(c.getId(), c.getName(), c.getCategory(), c.getStatus(), c.isMarketing(),
                c.isSendEmail(), c.isSendPush(), c.isSendInapp(), c.getResolvedRecipientCount(),
                c.getCreatedBy().getFullName(), c.getScheduledAt(), c.getSentAt(), c.getCreatedAt());
    }
}
