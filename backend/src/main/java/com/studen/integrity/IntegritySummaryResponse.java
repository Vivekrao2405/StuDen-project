package com.studen.integrity;

import java.time.Instant;
import java.util.UUID;

// Coarse, neutral summary -- score/status/counts only, never the deduction values or thresholds
// that produced them (goal #20 -- don't expose internal anti-cheating implementation details to
// students). `effectiveStatus` is `overrideStatus` if an admin has manually reviewed this attempt,
// else the deterministically computed `integrityStatus`.
public record IntegritySummaryResponse(
        UUID attemptId,
        Integer integrityScore,
        IntegrityStatus integrityStatus,
        IntegrityStatus effectiveStatus,
        boolean overridden,
        String overrideReason,
        String overrideByName,
        Instant overriddenAt,
        int totalEvents,
        int suspiciousEvents,
        int criticalEvents,
        int tabSwitchCount,
        int copyAttemptCount,
        int pasteAttemptCount,
        int cutAttemptCount,
        int fullscreenExitCount,
        int multipleSessionCount,
        Instant firstSuccessfulCompilationAt,
        int runCount,
        int submissionCount,
        int compilationFailureCount) {
}
