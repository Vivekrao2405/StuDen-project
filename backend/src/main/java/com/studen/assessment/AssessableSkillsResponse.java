package com.studen.assessment;

import com.studen.portfolio.EligibilityState;
import java.util.List;

// Wraps the (now portfolio-scoped) assessable-skill list with the explicit eligibility state that
// produced it — the frontend renders one of 4 states instead of ever treating an empty list as
// "no assessments exist" when it might actually mean "no portfolio" or "no skills yet".
public record AssessableSkillsResponse(EligibilityState state, List<AssessableSkillResponse> skills) {
}
