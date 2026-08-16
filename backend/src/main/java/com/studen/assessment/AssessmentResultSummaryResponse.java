package com.studen.assessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Phase 7.3's compact result summary — GET /assessments/{id}/result. Deliberately separate from
// AssessmentResultResponse (the existing per-question review payload): this is the scored,
// level-mapped, topic-broken-down view the result page/dashboard actually render. Only ever
// returned for a terminal (SUBMITTED/EXPIRED) assessment — SkillResultService 409s otherwise, so
// an IN_PROGRESS assessment's final scoring is never exposed early (spec §10).
//
// `level` is an assessment-derived level, never a "verified skill" claim (spec §20) — the
// frontend must render it as "Assessment Level: X", not "Verified Skill: X".
public record AssessmentResultSummaryResponse(UUID assessmentId, UUID skillId, String skillName,
        AssessmentStatus status, int totalQuestions, int correctCount, int incorrectCount, int scorePercentage,
        AssessmentLevel level, List<TopicPerformanceView> topicPerformance, PerformanceSummaryView summary,
        Instant startedAt, Instant submittedAt) {
}
