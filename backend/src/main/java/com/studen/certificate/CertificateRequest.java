package com.studen.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.hibernate.validator.constraints.URL;

public record CertificateRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 255, message = "Issued by must be at most 255 characters")
        String issuedBy,

        @PastOrPresent(message = "Issue date must not be in the future")
        LocalDate issueDate,

        @URL(message = "Certificate URL must be a valid URL")
        @Size(max = 500, message = "Certificate URL must be at most 500 characters")
        String certificateUrl) {
}
