package com.studen.practical;

import java.time.Instant;
import java.util.UUID;

public record AdminPracticalAttemptSummaryResponse(
        UUID id,
        UUID practicalAssessmentId,
        String assessmentTitle,
        UUID studentId,
        String studentName,
        PracticalAttemptStatus status,
        Instant startedAt,
        Instant submittedAt) {

    public static AdminPracticalAttemptSummaryResponse from(PracticalAttempt entity) {
        return new AdminPracticalAttemptSummaryResponse(entity.getId(), entity.getPracticalAssessment().getId(),
                entity.getPracticalAssessment().getTitle(), entity.getUser().getId(), entity.getUser().getFullName(),
                entity.getStatus(), entity.getStartedAt(), entity.getSubmittedAt());
    }
}
