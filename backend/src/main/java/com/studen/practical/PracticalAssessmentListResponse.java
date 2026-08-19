package com.studen.practical;

import com.studen.portfolio.EligibilityState;

// Wraps the (now portfolio-scoped) practical assessment page with the explicit eligibility state
// that produced it — mirrors com.studen.assessment.AssessableSkillsResponse for the knowledge
// side, same reasoning: an empty page must never be indistinguishable from "no portfolio yet".
public record PracticalAssessmentListResponse(EligibilityState state,
        PracticalPageResponse<PracticalAssessmentSummaryResponse> page) {
}
