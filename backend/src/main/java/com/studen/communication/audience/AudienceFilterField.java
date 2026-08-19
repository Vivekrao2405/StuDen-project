package com.studen.communication.audience;

/**
 * Every leaf condition type the admin Filter Builder UI can produce, compiled by
 * {@link AudienceSpecificationBuilder} into one JPA predicate each. Deliberately does not include
 * Challenge- or My-Learning-based fields — no backend exists for either yet (mirrors the spec's
 * own deferral of My Learning), so those simply aren't options in the builder today. Adding them
 * later only means adding new enum values + builder cases, never touching an existing one.
 */
public enum AudienceFilterField {
    PORTFOLIO_EXISTS,
    PORTFOLIO_NOT_EXISTS,
    PORTFOLIO_UPDATED_WITHIN_DAYS,
    PORTFOLIO_STALE_SINCE_DAYS,

    SKILL_HAS,
    SKILL_LACKS,
    SKILL_HAS_ANY,
    SKILL_HAS_ALL,

    // ASSESSMENT_* conditions read com.studen.assessment.Assessment (knowledge assessments only —
    // practical assessments use a separate PracticalAttempt model and aren't wired into audience
    // filters this phase). `skillId` param is optional on all four: omitted, they match against
    // any skill's assessment history rather than one specific skill.
    ASSESSMENT_COMPLETED,
    ASSESSMENT_NOT_COMPLETED,
    ASSESSMENT_NO_ATTEMPT,
    ASSESSMENT_SCORE_GTE,
    ASSESSMENT_SCORE_RANGE,

    // Reuses com.studen.assessment.AssessmentLevel/ScoringProperties as-is — the existing
    // assessment-derived level *is* the "skill verification" architecture; no separate scoring
    // system is introduced here.
    SKILL_VERIFICATION_LEVEL,
    SKILL_VERIFICATION_UNVERIFIED,

    ACTIVITY_LAST_ACTIVE_BEFORE_DAYS,
    ACTIVITY_REGISTERED_BETWEEN,

    USER_VERIFIED,
    USER_UNVERIFIED,
    USER_ACTIVE,
    USER_INACTIVE,
    USER_SPECIFIC_IDS
}
