package com.studen.practical;

import java.time.Instant;
import java.util.UUID;

// Read-only evidence surfaced to the existing skill-evaluation UI alongside the knowledge-
// assessment result — 7.4 provides this as additional evidence only; it does not compute or own
// the skill's overall readiness (spec §30), which stays entirely owned by
// com.studen.assessment.SkillResultService/ScoringProperties.
public record PracticalEvidenceResponse(
        UUID skillId,
        String skillName,
        UUID practicalAssessmentId,
        String assessmentTitle,
        Integer score,
        Integer maxScore,
        Instant evaluatedAt) {
}
