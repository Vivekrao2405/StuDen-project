package com.studen.assessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Returned by GET /assessments/{id} and POST /assessments (start/resume) while status is
// IN_PROGRESS. `remainingSeconds` is computed server-side from startedAt/timeLimitSeconds on every
// call — the frontend must never treat a purely client-side countdown as authoritative (spec §23).
public record AssessmentDetailResponse(UUID id, UUID skillId, String skillName, AssessmentType assessmentType,
        AssessmentStatus status, int totalQuestions, Instant startedAt, Integer timeLimitSeconds,
        Integer remainingSeconds, List<AssessmentQuestionView> questions) {
}
