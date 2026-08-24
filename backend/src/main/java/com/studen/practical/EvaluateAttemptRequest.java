package com.studen.practical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

// Phase 7.6 — one evaluation action scores every question in the attempt at once (mirrors how
// the pre-7.6 single-question evaluate form already worked, just looped). `questions` must cover
// every PracticalAttemptQuestion on the attempt — AdminPracticalAttemptService rejects a partial
// submission rather than silently leaving some questions unscored.
public record EvaluateAttemptRequest(

        @Valid @NotEmpty
        List<QuestionEvaluationEntry> questions,

        // Attempt-level overall remark — independent of each question's own `feedback` below.
        String feedback) {

    // Either `rubricScores` (rubric-based — the question's earned points are always
    // sum(points)/100*question.points, computed server-side, never trusted from a total) or a
    // direct `score` (non-rubric questions, 0..question.points) is supplied, never both
    // meaningfully at once.
    public record QuestionEvaluationEntry(
            @NotNull UUID attemptQuestionId,
            @Valid List<RubricScoreEntry> rubricScores,
            Integer score,
            String feedback) {

        public record RubricScoreEntry(
                @NotNull UUID criterionId,
                int points) {
        }
    }
}
