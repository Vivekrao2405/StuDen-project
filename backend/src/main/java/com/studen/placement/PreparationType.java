package com.studen.placement;

// What kind of preparation a PlacementSeries focuses on (spec §2's "Preparation Type" selector).
// Nullable on the series itself: a series that covers everything simply leaves this unset rather
// than being forced to pick FULL_PREPARATION, and student filtering treats "not set" as matching
// any preparation type search.
public enum PreparationType {
    TECHNICAL,
    CODING,
    APTITUDE,
    INTERVIEW,
    FULL_PREPARATION
}
