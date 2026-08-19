package com.studen.integrity;

// CLEAN/LOW_CONCERN/REVIEW/HIGH_CONCERN are the only statuses IntegrityScoringService ever
// assigns automatically, driven purely by IntegrityScoringProperties' score thresholds.
// INVALIDATED is reachable ONLY through an explicit admin manual override
// (AdminIntegrityService#override) -- nothing in this codebase ever sets it on its own, per the
// spec's "never automatically label someone a cheater" rule.
public enum IntegrityStatus {
    CLEAN,
    LOW_CONCERN,
    REVIEW,
    HIGH_CONCERN,
    INVALIDATED
}
