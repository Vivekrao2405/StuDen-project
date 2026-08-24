package com.studen.practical;

import java.util.List;
import java.util.UUID;

// The actual problem content for one question, only ever returned once a real attempt exists for
// the caller (see PracticalAttemptResponse's javadoc for the security rationale — identical to the
// pre-7.6 single-question model, just one row per question now instead of one field set on the
// whole response).
public record PracticalAttemptQuestionResponse(
        UUID id,
        UUID practicalQuestionId,
        String title,
        int displayOrder,
        int points,
        PracticalAttemptQuestionStatus status,
        String submissionContent,
        CodingLanguage selectedLanguage,
        String submissionLinkUrl,
        String submissionFileUrl,
        String instructions,
        String requirements,
        String constraints,
        String configurationJson,
        List<PracticalCodingLanguageResponse> languages,
        List<StudentTestCaseView> publicTestCases) {
}
