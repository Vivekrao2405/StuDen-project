package com.studen.assessment;

import com.studen.portfolio.EligibilityState;
import java.util.List;

// Wraps the full published-assessment skill catalog with an explicit state so the frontend never
// has to guess whether an empty list means "nothing published yet" (NO_MATCHING_ASSESSMENTS).
public record AssessableSkillsResponse(EligibilityState state, List<AssessableSkillResponse> skills) {
}
