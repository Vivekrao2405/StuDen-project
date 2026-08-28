package com.studen.resource;

import java.util.List;
import java.util.UUID;

// One group per weak skill, items already ordered (severity descending, i.e. worst-percentage
// topic first, tie-broken by the existing alphabetical needsImprovementTopics order — see
// RoadmapService for why no dependency/prerequisite graph is invented here).
public record RoadmapSkillGroupResponse(UUID skillId, String skillName, List<RoadmapItemResponse> items) {
}
