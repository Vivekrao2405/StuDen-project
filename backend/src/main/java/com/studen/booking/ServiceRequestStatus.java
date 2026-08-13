package com.studen.booking;

// Only PENDING is reachable in Phase 6.5 — the rest of the enum exists now so the entity/DB shape
// doesn't need another migration when Phase 6.6 (accept/reject) and 6.8 (work workflow) land.
public enum ServiceRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    SUBMITTED,
    COMPLETED,
    CANCELLED
}
