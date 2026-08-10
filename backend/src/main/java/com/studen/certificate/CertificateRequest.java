package com.studen.certificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CertificateRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 255, message = "Issued by must be at most 255 characters")
        String issuedBy,

        @PastOrPresent(message = "Issue date must not be in the future")
        LocalDate issueDate,

        // Rendered as an <a href> on both the owner's dashboard and their public profile, so this
        // must reject non-http(s) schemes (javascript:, data:, vbscript:, ...) server-side rather
        // than trusting the frontend's own equivalent check — a direct API call bypasses that.
        @Pattern(regexp = "^$|^https?://.+", message = "Certificate URL must start with http:// or https://")
        @Size(max = 500, message = "Certificate URL must be at most 500 characters")
        String certificateUrl) {
}
