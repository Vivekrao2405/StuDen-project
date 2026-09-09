package com.studen.placement;

import com.studen.resource.ResourceCardResponse;
import java.util.List;
import java.util.UUID;

// One priority-gap entry of the personalized learning plan — the gap fields (rank/status/priority/
// weight) are copied straight off the already-ranked PlacementSkillGapView (never re-sorted here),
// with the matching published resources for that skill attached. `resources` may legitimately be
// empty (a real gap with nothing mapped yet) -- the frontend must render that as a real empty
// state, never hide the gap or fabricate a resource for it.
public record PlacementSkillPlanResponse(int rank, UUID skillId, String skillName, int scorePercentage,
        SkillReadinessStatus status, int roleSkillPriority, int roleSkillWeight,
        List<ResourceCardResponse> resources) {
}
