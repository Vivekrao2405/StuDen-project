package com.studen.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceLinkRequest(

        @NotBlank(message = "Link label is required")
        @Size(max = 100, message = "Link label must be at most 100 characters")
        String label,

        // Rendered as an <a href> on the service detail page, so non-http(s) schemes must be
        // rejected server-side — a direct API call bypasses any frontend-only check. Same
        // convention as ProjectLinkRequest.url.
        @NotBlank(message = "Link URL is required")
        @Pattern(regexp = "^https?://.+", message = "Link URL must start with http:// or https://")
        @Size(max = 500, message = "Link URL must be at most 500 characters")
        String url) {
}
