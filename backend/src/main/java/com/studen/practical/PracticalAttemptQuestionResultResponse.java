package com.studen.practical;

import java.util.List;
import java.util.UUID;

// One question's final graded result (spec §10/§14/§21/§22). rubricScores is only ever non-empty
// for a MANUAL/HYBRID-graded question; testsPassed/testsTotal only ever set for an AUTOMATED
// CODING/SQL one -- the two are mutually exclusive in practice, both fields always present so the
// frontend doesn't need to branch on evaluationType to know which to render.
public record PracticalAttemptQuestionResultResponse(
        UUID practicalQuestionId,
        String title,
        int pointsPossible,
        Integer pointsEarned,
        Integer testsPassed,
        Integer testsTotal,
        PracticalAttemptQuestionStatus status,
        String feedback,
        List<RubricScoreView> rubricScores) {
}
