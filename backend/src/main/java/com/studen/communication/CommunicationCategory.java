package com.studen.communication;

/**
 * The 12 communication categories shared by templates, campaigns, and analytics. CHALLENGE/
 * CHALLENGE_WINNER/MY_LEARNING are kept here (unlike the audience-filter enums) because they're
 * just labels an admin can pick for a CUSTOM-style campaign today — using them doesn't require a
 * Challenge or My Learning backend to exist, only audience *filtering* by those concepts does.
 */
public enum CommunicationCategory {
    CHALLENGE,
    CHALLENGE_WINNER,
    ASSESSMENT,
    ASSESSMENT_REMINDER,
    ASSESSMENT_RESULT,
    LEARNING,
    MY_LEARNING,
    OPPORTUNITY,
    MARKETPLACE,
    SYSTEM_ANNOUNCEMENT,
    PRODUCT_UPDATE,
    MARKETING,
    CUSTOM
}
