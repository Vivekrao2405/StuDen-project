package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Returned by POST (start/resume) and GET /attempts/{id} while status is IN_PROGRESS.
// `remainingSeconds` is computed server-side from startedAt + the assessment's durationMinutes on
// every call — the frontend must never treat a purely client-side countdown as authoritative.
public record PlacementAttemptDetailResponse(UUID id, UUID placementAssessmentId, String assessmentTitle,
        UUID roleId, String roleName, PlacementAttemptStatus status, int totalQuestions, Instant startedAt,
        Integer timeLimitSeconds, Integer remainingSeconds, List<PlacementAttemptQuestionView> questions) {
}
