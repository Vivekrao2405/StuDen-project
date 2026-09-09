package com.studen.placement;

import java.util.UUID;

// Backs the Placement Readiness entry screen: what's available for this student, and whether they
// have an attempt already in progress (to offer "Resume" instead of "Start"). `latestResult` is
// null until the student has completed at least one attempt for this role's assessment.
public record PlacementReadinessStatusResponse(PlacementReadinessState state, UUID targetRoleId,
        String targetRoleName, UUID assessmentId, String assessmentTitle, Integer durationMinutes,
        int questionCount, UUID inProgressAttemptId, PlacementReadinessResultResponse latestResult) {
}
