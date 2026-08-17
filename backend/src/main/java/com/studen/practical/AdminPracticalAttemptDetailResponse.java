package com.studen.practical;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Admin-only — includes the full submission plus every test case (hidden included), since an
// admin evaluating a CODING attempt needs to see whether it actually produces the expected
// (possibly hidden) output.
public record AdminPracticalAttemptDetailResponse(
        UUID id,
        UUID practicalAssessmentId,
        String assessmentTitle,
        PracticalType practicalType,
        UUID studentId,
        String studentName,
        PracticalAttemptStatus status,
        Instant startedAt,
        Instant deadline,
        Instant submittedAt,
        Instant evaluatedAt,
        Integer score,
        Integer maxScore,
        String feedback,
        CodingLanguage selectedLanguage,
        String submissionContent,
        String submissionFileUrl,
        String submissionLinkUrl,
        List<PracticalTestCaseResponse> testCases,
        List<PracticalRubricCriterionResponse> rubricCriteria,
        List<RubricScoreView> rubricScores) {
}
