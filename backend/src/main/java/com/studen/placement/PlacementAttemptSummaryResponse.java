package com.studen.placement;

import java.time.Instant;
import java.util.UUID;

// One row of a student's own readiness-attempt history — backs "previous vs latest" without
// exposing any other student's data (scoped by PlacementAttemptRepository.findByStudentId...).
public record PlacementAttemptSummaryResponse(UUID id, UUID placementAssessmentId, String assessmentTitle,
        UUID roleId, String roleName, PlacementAttemptStatus status, Integer scorePercentage, Instant startedAt,
        Instant submittedAt) {

    public static PlacementAttemptSummaryResponse from(PlacementAttempt attempt) {
        PlacementAssessment assessment = attempt.getPlacementAssessment();
        return new PlacementAttemptSummaryResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getRole().getId(), assessment.getRole().getName(), attempt.getStatus(),
                attempt.getScorePercentage(), attempt.getStartedAt(), attempt.getSubmittedAt());
    }
}
