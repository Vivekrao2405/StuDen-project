package com.studen.booking;

// ACCEPTED/REJECTED are terminal (Phase 6.6) — a request never reaches IN_PROGRESS/COMPLETED/etc.
// itself. The post-acceptance work lifecycle lives on its own com.studen.orders.WorkOrder entity
// (Phase 6.8, one per ACCEPTED request) with its own OrderStatus, rather than growing this enum
// further — see the Phase 6.8 plan for why a separate entity was chosen over reusing this one.
public enum ServiceRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
