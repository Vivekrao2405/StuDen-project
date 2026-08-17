package com.studen.practical;

import java.time.Instant;
import java.util.UUID;

// Returned while an attempt is IN_PROGRESS. `remainingSeconds` is recomputed fresh on every fetch
// from `deadline` — the frontend only ever displays it, never decides expiry itself (mirrors
// AssessmentDetailResponse.remainingSeconds exactly).
public record PracticalAttemptResponse(
        UUID id,
        UUID practicalAssessmentId,
        String title,
        PracticalType practicalType,
        WorkspaceType workspaceType,
        PracticalAttemptStatus status,
        Instant startedAt,
        Instant deadline,
        Long remainingSeconds,
        String submissionContent,
        CodingLanguage selectedLanguage,
        String submissionLinkUrl,
        String submissionFileUrl) {
}
