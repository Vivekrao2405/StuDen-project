package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Returned by GET /attempts/{id} once status is SUBMITTED or EXPIRED — the raw per-question review
// payload. Only ever visible to the owning student, enforced by
// PlacementAttemptRepository.findByIdAndStudentId, not here.
public record PlacementAttemptReviewResponse(UUID id, UUID placementAssessmentId, String assessmentTitle,
        UUID roleId, String roleName, PlacementAttemptStatus status, int totalQuestions, Integer correctCount,
        Integer scorePercentage, Instant startedAt, Instant submittedAt,
        List<PlacementAttemptResultQuestionView> questions) {
}
