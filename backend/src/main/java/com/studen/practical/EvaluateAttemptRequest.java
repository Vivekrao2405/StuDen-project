package com.studen.practical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

// Either `rubricScores` (rubric-based MANUAL/HYBRID evaluation — the final score is always
// sum(points) computed server-side, never trusted from `score`) or a direct `score` (non-rubric
// types) is supplied, never both meaningfully at once. `feedback` is always optional free text.
public record EvaluateAttemptRequest(
        @Valid List<RubricScoreEntry> rubricScores,
        Integer score,
        String feedback) {

    public record RubricScoreEntry(
            @NotNull UUID criterionId,
            int points) {
    }
}
