package com.studen.resource;

// topicsCompleted/topicsTotal count roadmap items (topics), not resources — a topic counts as
// completed only when every one of its matched published resources is COMPLETED (see
// RoadmapService). percentage is topicsTotal==0 ? 0 : round(topicsCompleted*100/topicsTotal),
// computed server-side once here rather than left for every consumer to redo.
public record RoadmapOverviewResponse(int topicsCompleted, int topicsTotal, int percentage) {

    public static RoadmapOverviewResponse empty() {
        return new RoadmapOverviewResponse(0, 0, 0);
    }
}
