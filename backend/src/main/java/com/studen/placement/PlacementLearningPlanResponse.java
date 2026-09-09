package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Backs the "Your Placement Learning Path" section of My Learning (spec §9/§12) — GET
// /api/v1/placement/learning-plan. Server-authoritative throughout: prioritySkills is a direct
// projection of the student's own latest PlacementReadinessResultResponse.priorityGaps (already
// ranked by PlacementReadinessService), each paired with real published, admin-mapped resources.
public record PlacementLearningPlanResponse(PlacementLearningPlanState state, UUID targetRoleId,
        String targetRoleName, UUID latestAttemptId, Instant latestResultAt,
        List<PlacementSkillPlanResponse> prioritySkills) {

    public static PlacementLearningPlanResponse empty(PlacementLearningPlanState state, UUID targetRoleId,
            String targetRoleName) {
        return new PlacementLearningPlanResponse(state, targetRoleId, targetRoleName, null, null, List.of());
    }
}
