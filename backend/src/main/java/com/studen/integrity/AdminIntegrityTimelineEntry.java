package com.studen.integrity;

import java.time.Instant;

// One row in the admin-facing merged timeline (goal #19) -- combines attempt lifecycle events,
// com.studen.practical.execution.ExecutionJob rows, and AssessmentIntegrityEvent rows into a
// single chronological feed, without a second storage system.
public record AdminIntegrityTimelineEntry(
        Instant timestamp,
        TimelineCategory category,
        String label,
        String detail,
        IntegritySeverity severity) {

    public enum TimelineCategory {
        LIFECYCLE,
        EXECUTION,
        INTEGRITY
    }
}
