package com.studen.share;

import com.studen.certificate.Certificate;
import java.time.LocalDate;

public record PublicCertificateItem(
        String title,
        String issuedBy,
        LocalDate issueDate,
        String certificateUrl) {

    public static PublicCertificateItem from(Certificate certificate) {
        return new PublicCertificateItem(
                certificate.getTitle(),
                certificate.getIssuedBy(),
                certificate.getIssueDate(),
                certificate.getCertificateUrl());
    }
}
