package com.studen.communication;

public record TemplateRequest(
        String name,
        CommunicationCategory category,
        String emailSubject,
        String emailBodyHtml,
        String pushTitle,
        String pushBody,
        String inappTitle,
        String inappBody,
        String ctaText,
        String ctaUrl) {
}
