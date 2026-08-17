package com.studen.practical;

// Partial-update autosave body — every field optional, only non-null fields are applied. Mirrors
// the PATCH-semantics already used elsewhere in the app (e.g. UpdateUserRequest-style partials).
public record SaveAttemptRequest(
        String submissionContent,
        CodingLanguage selectedLanguage,
        String submissionLinkUrl) {
}
