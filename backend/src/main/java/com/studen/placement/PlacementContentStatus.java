package com.studen.placement;

// Lifecycle for admin-authored placement content (assessments, series). Matches the three
// statuses the spec names for a Placement Series, and mirrors ResourceStatus exactly (no REVIEW
// stage — the spec does not call for one here).
public enum PlacementContentStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
