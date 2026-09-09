package com.studen.placement;

import com.studen.resource.ResourceCardResponse;
import com.studen.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Standard authentication only (no ADMIN gate) — same posture as PlacementReadinessController.
// Every lookup is derived entirely from the caller's own PlacementProfile/attempts inside
// PlacementLearningPlanService; this controller never accepts a client-supplied student id.
@RestController
@RequestMapping("/api/v1/placement/learning-plan")
public class PlacementLearningPlanController {

    private final PlacementLearningPlanService service;

    public PlacementLearningPlanController(PlacementLearningPlanService service) {
        this.service = service;
    }

    // The full personalized plan — also serves as "latest placement learning recommendations"
    // (spec §20), since it is always derived from the student's latest terminal readiness attempt.
    @GetMapping
    public PlacementLearningPlanResponse getLearningPlan(@AuthenticationPrincipal UserPrincipal principal) {
        return service.getLearningPlan(principal.getId());
    }

    // Recommendations scoped to one specific current priority-gap skill (used by the Placement
    // Readiness result page's "Improve this skill" entry point into My Learning).
    @GetMapping("/skills/{skillId}/resources")
    public List<ResourceCardResponse> getRecommendationsForSkill(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID skillId) {
        return service.getRecommendationsForSkill(principal.getId(), skillId);
    }
}
