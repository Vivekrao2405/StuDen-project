package com.studen.communication;

import java.time.Instant;
import java.util.UUID;

public record CampaignDetailResponse(
        UUID id,
        String name,
        CommunicationCategory category,
        CampaignStatus status,
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
        String ctaUrl,
        Integer resolvedRecipientCount,
        String createdByName,
        Instant scheduledAt,
        Instant processingStartedAt,
        Instant sentAt,
        Instant createdAt) {

    public static CampaignDetailResponse from(CommunicationCampaign c) {
        return new CampaignDetailResponse(c.getId(), c.getName(), c.getCategory(), c.getStatus(), c.isMarketing(),
                c.getFilterJson(), c.getTemplate() == null ? null : c.getTemplate().getId(),
                c.getSegment() == null ? null : c.getSegment().getId(), c.isSendEmail(), c.isSendPush(),
                c.isSendInapp(), c.getEmailSubject(), c.getEmailBodyHtml(), c.getPushTitle(), c.getPushBody(),
                c.getInappTitle(), c.getInappBody(), c.getCtaText(), c.getCtaUrl(), c.getResolvedRecipientCount(),
                c.getCreatedBy().getFullName(), c.getScheduledAt(), c.getProcessingStartedAt(), c.getSentAt(),
                c.getCreatedAt());
    }
}
