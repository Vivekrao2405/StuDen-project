package com.studen.practical;

import com.studen.integrity.IntegrityPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Returned while an attempt is IN_PROGRESS (start/resume/get/autosave). Deliberately the ONLY
// student-facing response that ever carries actual problem content (each PracticalAttemptQuestion
// in `questions` carries its own instructions/requirements/constraints/configurationJson/
// languages-with-starter-code/publicTestCases) — safe here specifically because every call site
// builds this off a PracticalAttempt the caller has already proven ownership of via
// PracticalAttemptRepository's userId-scoped lookups (see PracticalAttemptService.findOwnAttempt/
// startOrResume). The pre-start StudentPracticalAssessmentResponse (GET /practical-assessments/{id})
// deliberately excludes all of it — do not move problem content back there. `remainingSeconds` is
// recomputed fresh on every fetch from `deadline` — the frontend only ever displays it, never
// decides expiry itself (mirrors AssessmentDetailResponse.remainingSeconds exactly).
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
        List<PracticalAttemptQuestionResponse> questions,
        IntegrityPolicy integrityPolicy) {
}
