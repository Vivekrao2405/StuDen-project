package com.studen.practical;

import com.studen.integrity.IntegrityPolicy;
import com.studen.questionbank.Difficulty;
import java.util.List;
import java.util.UUID;

// Pre-start metadata ONLY — returned by GET /practical-assessments/{id}, reachable by any
// authenticated student for any PUBLISHED assessment, before any attempt exists. Deliberately
// excludes the actual problem: no requirements (problem statement), constraints, test cases,
// starter code, or configurationJson. That content is only ever returned once a real
// PracticalAttempt exists for the caller — see PracticalAttemptResponse, built by
// PracticalAttemptService off userId-scoped attempt lookups. Do not add problem-content fields
// back onto this record; extend PracticalAttemptResponse instead. `integrityPolicy` is the one
// exception — it's rules-of-engagement metadata (Phase 7.6), not problem content, needed to show
// an accurate pre-start "During this assessment..." notice.
public record StudentPracticalAssessmentResponse(
        UUID id,
        String title,
        UUID skillId,
        String skillName,
        PracticalType practicalType,
        WorkspaceType workspaceType,
        Difficulty difficulty,
        int timeLimitMinutes,
        String instructions,
        int questionCount,
        List<CodingLanguage> supportedLanguages,
        IntegrityPolicy integrityPolicy) {
}
