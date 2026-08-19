package com.studen.communication;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        CommunicationCategory category,
        String emailSubject,
        String emailBodyHtml,
        String pushTitle,
        String pushBody,
        String inappTitle,
        String inappBody,
        String ctaText,
        String ctaUrl,
        boolean archived,
        String createdByName,
        Instant createdAt) {

    public static TemplateResponse from(CommunicationTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getCategory(), t.getEmailSubject(),
                t.getEmailBodyHtml(), t.getPushTitle(), t.getPushBody(), t.getInappTitle(), t.getInappBody(),
                t.getCtaText(), t.getCtaUrl(), t.isArchived(), t.getCreatedBy().getFullName(), t.getCreatedAt());
    }
}
