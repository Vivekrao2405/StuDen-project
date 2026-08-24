package com.studen.practical;

import java.util.List;
import java.util.UUID;

// Admin-only per-question detail — includes the full submission plus every test case (hidden
// included), since an admin evaluating a CODING attempt needs to see whether it actually produces
// the expected (possibly hidden) output.
public record AdminAttemptQuestionDetailResponse(
        UUID attemptQuestionId,
        UUID practicalQuestionId,
        String title,
        int pointsPossible,
        Integer pointsEarned,
        Integer testsPassed,
        Integer testsTotal,
        PracticalAttemptQuestionStatus status,
        String feedback,
        CodingLanguage selectedLanguage,
        String submissionContent,
        String submissionFileUrl,
        String submissionLinkUrl,
        List<PracticalTestCaseResponse> testCases,
        List<PracticalRubricCriterionResponse> rubricCriteria,
        List<RubricScoreView> rubricScores,
        List<ExecutionJobSummaryResponse> executionHistory) {
}
