package com.studen.resource;

// My Learning's top overview card. All fields are real aggregates, never invented:
// - weakSkillsCount: number of portfolio skills currently carrying a weak area (groups.size()).
// - resourcesCount: total resources recommended across all groups (sum of each group's capped list).
// - assessmentsCompletedCount: portfolio skills with at least one completed MCQ or practical
//   assessment (see WeakAreaAggregationService#countCompletedAssessments) — a "have I assessed this
//   skill" count, not a raw attempt-history count (no such count is exposed elsewhere in the app).
// - completedResourceCount/totalResourceCount: sum of each group's completedCount/totalCount, the
//   basis for the overall progress percentage (computed client-side to avoid a redundant division).
public record LearningOverviewResponse(
        int weakSkillsCount,
        int resourcesCount,
        int assessmentsCompletedCount,
        int completedResourceCount,
        int totalResourceCount) {

    public static LearningOverviewResponse empty() {
        return new LearningOverviewResponse(0, 0, 0, 0, 0);
    }
}
