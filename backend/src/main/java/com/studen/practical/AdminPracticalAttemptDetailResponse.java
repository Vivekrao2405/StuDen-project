package com.studen.practical;

import com.studen.integrity.IntegritySummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// `integrity` (Phase 7.6) is a fully independent evidence summary — never derived from or
// affecting score/maxScore above. `questions` (also Phase 7.6) replaces the old single-question
// selectedLanguage/submissionContent/testCases/rubricCriteria/rubricScores fields.
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
        List<AdminAttemptQuestionDetailResponse> questions,
        IntegritySummaryResponse integrity) {
}
