package com.studen.resource;

import com.studen.portfolio.EligibilityState;
import java.util.List;

// Reuses EligibilityState as-is (NO_PORTFOLIO/NO_SKILLS/HAS_AVAILABLE_ASSESSMENTS — its own
// javadoc already frames it as shared across "assessment-visibility" consumers, and "does this
// student have a usable skill profile" is exactly that same question for My Learning).
// NO_MATCHING_ASSESSMENTS is never used here; a portfolio skill with no weak area is simply
// omitted from `groups` under HAS_AVAILABLE_ASSESSMENTS rather than treated as a distinct state —
// "no weak areas yet" is a data outcome, not an eligibility gate.
// `overview` is LearningOverviewResponse.empty() whenever groups is empty (ineligible or no weak
// areas yet) — the frontend never needs to null-check it.
public record MyLearningResponse(EligibilityState state, List<WeakAreaGroupResponse> groups,
        LearningOverviewResponse overview) {
}
