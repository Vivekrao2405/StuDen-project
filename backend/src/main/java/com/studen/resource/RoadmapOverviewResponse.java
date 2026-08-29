package com.studen.resource;

// topicsCompleted/topicsTotal count roadmap items (topics), not resources — a topic counts as
// completed only when every one of its matched published resources is COMPLETED (see
// RoadmapService). percentage is topicsTotal==0 ? 0 : round(topicsCompleted*100/topicsTotal),
// computed server-side once here rather than left for every consumer to redo. currentStreakDays is
// real, derived from StudentResourceProgress.completedAt (see RoadmapService.computeStreak) — never
// a fabricated number; deliberately no "estimated completion date" field here, since this codebase
// has no pacing/velocity data reliable enough to project one honestly.
public record RoadmapOverviewResponse(int topicsCompleted, int topicsTotal, int percentage, int currentStreakDays) {

    public static RoadmapOverviewResponse empty() {
        return new RoadmapOverviewResponse(0, 0, 0, 0);
    }
}
