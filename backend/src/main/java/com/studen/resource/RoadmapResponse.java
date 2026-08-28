package com.studen.resource;

import com.studen.portfolio.EligibilityState;
import java.util.List;

// Reuses EligibilityState exactly like MyLearningResponse (NO_PORTFOLIO/NO_SKILLS/
// HAS_AVAILABLE_ASSESSMENTS — a portfolio skill with no weak area is simply absent from `groups`,
// not a distinct state). `allCaughtUp` is true only when the student has (or had) real weak areas
// and every matched resource for every one of them is now COMPLETED — the honest "100% learning
// path completion" state (TEST 10); it is false, not true, when the student simply has no weak
// areas at all (groups is empty for a different reason — nothing to be "caught up" on).
public record RoadmapResponse(EligibilityState state, List<RoadmapSkillGroupResponse> groups,
        RoadmapOverviewResponse overview, boolean allCaughtUp, RoadmapItemResponse nextUp) {

    public static RoadmapResponse empty(EligibilityState state) {
        return new RoadmapResponse(state, List.of(), RoadmapOverviewResponse.empty(), false, null);
    }
}
