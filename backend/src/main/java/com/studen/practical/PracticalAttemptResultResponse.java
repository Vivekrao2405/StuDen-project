package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PracticalAttemptResultResponse(
        UUID id,
        UUID practicalAssessmentId,
        String title,
        PracticalType practicalType,
        Difficulty difficulty,
        PracticalAttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Instant evaluatedAt,
        Integer score,
        Integer maxScore,
        String feedback,
        List<RubricScoreView> rubricScores) {
}
