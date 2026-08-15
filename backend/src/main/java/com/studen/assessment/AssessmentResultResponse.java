package com.studen.assessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Returned by GET /assessments/{id} once status is SUBMITTED or EXPIRED. Only ever visible to the
// owning student (spec §32) — enforced by AssessmentRepository.findByIdAndUserId, not here.
public record AssessmentResultResponse(UUID id, UUID skillId, String skillName, AssessmentType assessmentType,
        AssessmentStatus status, int totalQuestions, Integer correctCount, Integer scorePercentage,
        Instant startedAt, Instant submittedAt, List<AssessmentResultQuestionView> questions) {
}
