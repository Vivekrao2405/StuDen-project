package com.studen.resource;

// NOT_STARTED is never persisted — the absence of a StudentResourceProgress row for a
// (student, resource) pair *is* NOT_STARTED. Only IN_PROGRESS/COMPLETED are ever written.
public enum ResourceProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}
