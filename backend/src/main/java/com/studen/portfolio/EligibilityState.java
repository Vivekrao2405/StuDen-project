package com.studen.portfolio;

// Explicit assessment-visibility states shared by com.studen.assessment (knowledge) and
// com.studen.practical (practical) — both listing endpoints resolve one of these instead of ever
// silently falling back to "show everything". HAS_SKILLS is deliberately not a state of its own:
// once a portfolio has skills, the only two outcomes that matter to the UI are whether any
// assessment actually matches them.
public enum EligibilityState {
    NO_PORTFOLIO,
    NO_SKILLS,
    NO_MATCHING_ASSESSMENTS,
    HAS_AVAILABLE_ASSESSMENTS
}
