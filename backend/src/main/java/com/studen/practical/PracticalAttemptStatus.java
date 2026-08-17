package com.studen.practical;

// IN_PROGRESS -> UNDER_REVIEW -> EVALUATED, or IN_PROGRESS -> EXPIRED (backend-detected deadline
// miss, mirrors AssessmentStatus.EXPIRED). SUBMITTED exists in the schema for a future automated-
// judge phase (submit -> auto-scored SUBMITTED, or escalated to UNDER_REVIEW on failure) but isn't
// set by anything today — PracticalAttemptService.submit() goes straight to UNDER_REVIEW, since
// without a real judge there's no meaningful distinction between "submitted" and "awaiting manual
// review." CANCELLED is reserved for a future admin/student cancel action — not triggered anywhere
// yet.
public enum PracticalAttemptStatus {
    IN_PROGRESS,
    SUBMITTED,
    UNDER_REVIEW,
    EVALUATED,
    EXPIRED,
    CANCELLED
}
