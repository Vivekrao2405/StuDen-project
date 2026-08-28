package com.studen.resource;

import java.util.Comparator;
import java.util.UUID;

// One roadmap entry per weak topic (or, when a weak area is skill-scoped only — practical-sourced,
// no tags — one entry for the whole skill, mirroring FocusAreaTopicResponse's same fallback).
// `status` reuses ResourceProgressStatus as-is rather than a parallel enum: a topic's status is
// entirely derived from its matched resources' real StudentResourceProgress rows, never a second
// source of truth. `resource` is the single best not-yet-completed matching resource (null only
// when zero published resources currently match this topic — an honest gap, never fabricated).
public record RoadmapItemResponse(
        UUID skillId,
        String skillName,
        String topic,
        int percentage,
        ResourceProgressStatus status,
        RecommendationPriority priority,
        String reason,
        ResourceCardResponse resource,
        int completedCount,
        int totalCount) {

    // Shared "what's most urgent" ordering — an item with an actual resource to act on always
    // outranks one that currently has none (recommending "next up" a topic nothing can be started
    // for isn't a useful recommendation, even if it's technically the single worst percentage);
    // within that, HIGH before MEDIUM before LOW, worse percentage first within the same priority.
    // Used both for RoadmapService's own "next up" pick and com.studen.calendar.CalendarService's
    // study-plan queue, so the two features can never disagree about ordering.
    public static final Comparator<RoadmapItemResponse> PRIORITY_ORDER = Comparator
            .comparing((RoadmapItemResponse item) -> item.resource() == null)
            .thenComparing(item -> item.priority().ordinal())
            .thenComparingInt(RoadmapItemResponse::percentage);
}
