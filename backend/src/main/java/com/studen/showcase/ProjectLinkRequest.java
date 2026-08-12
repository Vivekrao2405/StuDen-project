package com.studen.showcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectLinkRequest(

        @NotBlank(message = "Link label is required")
        @Size(max = 100, message = "Link label must be at most 100 characters")
        String label,

        // Same reasoning as CertificateRequest.certificateUrl: rendered as an <a href> on the
        // project detail page, so non-http(s) schemes must be rejected server-side — a direct
        // API call bypasses any frontend-only check.
        @NotBlank(message = "Link URL is required")
        @Pattern(regexp = "^https?://.+", message = "Link URL must start with http:// or https://")
        @Size(max = 500, message = "Link URL must be at most 500 characters")
        String url) {
}
