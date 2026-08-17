package com.studen.practical;

import java.time.Instant;
import java.util.UUID;

public record MyPracticalAttemptSummaryResponse(
        UUID id,
        UUID practicalAssessmentId,
        String title,
        PracticalType practicalType,
        PracticalAttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Integer score,
        Integer maxScore) {

    public static MyPracticalAttemptSummaryResponse from(PracticalAttempt entity) {
        return new MyPracticalAttemptSummaryResponse(entity.getId(), entity.getPracticalAssessment().getId(),
                entity.getPracticalAssessment().getTitle(), entity.getPracticalAssessment().getPracticalType(),
                entity.getStatus(), entity.getStartedAt(), entity.getSubmittedAt(), entity.getScore(), entity.getMaxScore());
    }
}
