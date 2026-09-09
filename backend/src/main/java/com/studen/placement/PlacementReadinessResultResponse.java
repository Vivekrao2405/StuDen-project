package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// The scored, skill-broken-down, gap-ranked summary for one terminal attempt — GET
// /placement/readiness/attempts/{id}/result. Deliberately separate from
// PlacementAttemptReviewResponse (the per-question review payload), mirroring
// AssessmentResultSummaryResponse vs AssessmentResultResponse. Only ever returned for a terminal
// (SUBMITTED/EXPIRED) attempt.
public record PlacementReadinessResultResponse(UUID attemptId, UUID placementAssessmentId, String assessmentTitle,
        UUID roleId, String roleName, PlacementAttemptStatus status, int totalQuestions, int correctCount,
        int scorePercentage, List<PlacementSkillBreakdownView> skillBreakdown, List<PlacementSkillGapView> priorityGaps,
        Instant startedAt, Instant submittedAt) {
}
