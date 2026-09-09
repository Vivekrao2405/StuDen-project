package com.studen.placement;

import com.studen.questionbank.Difficulty;
import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student-facing Placement Prep (Phase 5) — browsing published series, opening one, and working
 * through its modules. No class-level {@code @PreAuthorize}: standard authenticated-by-default
 * security, same posture as {@code PlacementCatalogController}/{@code PlacementReadinessController}.
 * A student can only ever act on their own progress — every method resolves the caller strictly
 * from {@code UserPrincipal}, never from a path/body id.
 */
@RestController
@RequestMapping("/api/v1/placement/prep")
public class PlacementPrepController {

    private final PlacementPrepService service;

    public PlacementPrepController(PlacementPrepService service) {
        this.service = service;
    }

    @GetMapping("/series")
    public PlacementPageResponse<StudentPlacementSeriesResponse> listSeries(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) PreparationType preparationType,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listSeries(principal.getId(), roleId, companyId, companyType, preparationType, difficulty,
                skillId, search, page, size);
    }

    @GetMapping("/series/{id}")
    public StudentPlacementSeriesDetailResponse getSeries(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return service.getSeriesDetail(principal.getId(), id);
    }

    @PostMapping("/series/{id}/start")
    public StudentPlacementSeriesDetailResponse startSeries(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return service.startSeries(principal.getId(), id);
    }

    @GetMapping("/series/{id}/continue")
    public PlacementContinueResponse getContinuePointer(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return service.getContinuePointer(principal.getId(), id);
    }

    @GetMapping("/module-items/{itemId}")
    public PlacementModuleItemDetailResponse getModuleItem(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID itemId) {
        return service.getModuleItemDetail(principal.getId(), itemId);
    }

    @PostMapping("/module-items/{itemId}/answer")
    public PlacementModuleItemAnswerResponse answer(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID itemId, @Valid @RequestBody PlacementModuleItemAnswerRequest request) {
        return service.answerQuestionItem(principal.getId(), itemId, request.selectedOptionIds());
    }

    @PostMapping("/module-items/{itemId}/start-practical")
    public PlacementModuleItemDetailResponse startPractical(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID itemId) {
        return service.startPracticalItem(principal.getId(), itemId);
    }
}
