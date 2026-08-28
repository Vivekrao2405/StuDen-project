package com.studen.resource;

// Ordinal order is the priority order (HIGH first) — RoadmapService relies on this for picking
// "next up" via Comparator.comparing(priority -> priority.ordinal()). See RoadmapService.priorityFor
// for exactly how each item lands in a bucket: HIGH = not started, MEDIUM = in progress and under
// half of its matched resources done, LOW = in progress but mostly done (revision territory) or
// fully completed (never surfaced as "next" regardless of this label).
public enum RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW
}
