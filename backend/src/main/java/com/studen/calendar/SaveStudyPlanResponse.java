package com.studen.calendar;

import java.util.List;

// `skipped` lists the (0-based) indices of `sessions` in the request that exactly conflicted with
// an existing SCHEDULED session and were therefore not created — the student's calendar is never
// silently overwritten (section 14's explicit requirement).
public record SaveStudyPlanResponse(List<LearningSessionResponse> created, List<Integer> skipped) {
}
