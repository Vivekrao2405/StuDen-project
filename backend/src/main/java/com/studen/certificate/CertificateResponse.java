package com.studen.certificate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        String title,
        String issuedBy,
        LocalDate issueDate,
        String certificateUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static CertificateResponse from(Certificate certificate) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getTitle(),
                certificate.getIssuedBy(),
                certificate.getIssueDate(),
                certificate.getCertificateUrl(),
                certificate.getCreatedAt(),
                certificate.getUpdatedAt());
    }
}
