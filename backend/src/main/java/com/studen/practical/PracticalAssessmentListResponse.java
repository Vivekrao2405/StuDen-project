package com.studen.practical;

import com.studen.portfolio.EligibilityState;

// Wraps the full published practical-assessment page with an explicit state — mirrors
// com.studen.assessment.AssessableSkillsResponse for the knowledge side, same reasoning: an empty
// page must never be indistinguishable from "nothing published yet" (NO_MATCHING_ASSESSMENTS).
public record PracticalAssessmentListResponse(EligibilityState state,
        PracticalPageResponse<PracticalAssessmentSummaryResponse> page) {
}
