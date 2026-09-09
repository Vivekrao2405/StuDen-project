package com.studen.placement;

import java.util.UUID;

// One entry in "Priority Skill Gaps" — `rank` is 1-based ordering already applied server-side
// (see PlacementReadinessService.rankGaps); the frontend renders this list as-is and must never
// re-sort it by score alone (spec: gaps are ranked by configured RoleSkill priority/weight, not
// "lowest score wins").
public record PlacementSkillGapView(int rank, UUID skillId, String skillName, int scorePercentage,
        SkillReadinessStatus status, int roleSkillPriority, int roleSkillWeight) {
}
