package com.studen.orders;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// link is optional (blank string allowed) — the pattern anchors on http(s):// so a javascript:
// or data: URI is rejected outright rather than merely discouraged, matching every other
// optional-URL field in this codebase (e.g. ServiceRequestLinkRequest).
public record SubmitWorkRequest(

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Pattern(regexp = "^$|^https?://.+", message = "Link must be a valid http(s) URL")
        @Size(max = 500, message = "Link must be at most 500 characters")
        String link) {
}
