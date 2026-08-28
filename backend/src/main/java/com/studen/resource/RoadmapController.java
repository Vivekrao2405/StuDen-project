package com.studen.resource;

import com.studen.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// No class-level @PreAuthorize — same authenticated-by-default posture as ResourceController.
// Every response is scoped to principal.getId(); no path/query parameter ever selects another
// student's data.
@RestController
@RequestMapping("/api/v1/roadmap")
public class RoadmapController {

    private final RoadmapService roadmapService;

    public RoadmapController(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @GetMapping
    public RoadmapResponse roadmap(@AuthenticationPrincipal UserPrincipal principal) {
        return roadmapService.computeRoadmap(principal.getId());
    }

    @GetMapping("/recommendations")
    public RecommendationResponse recommendations(@AuthenticationPrincipal UserPrincipal principal) {
        return roadmapService.recommendations(principal.getId());
    }

    @GetMapping("/progress")
    public RoadmapOverviewResponse progress(@AuthenticationPrincipal UserPrincipal principal) {
        return roadmapService.progress(principal.getId());
    }
}
