package com.studen.placement;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Standard authentication only (no ADMIN gate) — same posture as AssessmentController. Every
// lookup is scoped to the calling student inside the two services; this controller never trusts a
// client-supplied ownership claim, and never accepts a client-supplied score.
@RestController
@RequestMapping("/api/v1/placement/readiness")
public class PlacementReadinessController {

    private final PlacementAttemptService attemptService;
    private final PlacementReadinessService readinessService;

    public PlacementReadinessController(PlacementAttemptService attemptService, PlacementReadinessService readinessService) {
        this.attemptService = attemptService;
        this.readinessService = readinessService;
    }

    // Backs the Placement Readiness entry screen: profile/target-role/assessment availability,
    // any resumable in-progress attempt, and the latest completed result.
    @GetMapping("/status")
    public PlacementReadinessStatusResponse getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return readinessService.getStatus(principal.getId());
    }

    @GetMapping("/attempts")
    public List<PlacementAttemptSummaryResponse> listMyAttempts(@AuthenticationPrincipal UserPrincipal principal) {
        return attemptService.listMyAttempts(principal.getId());
    }

    // Starts a brand-new readiness attempt for the caller's own target role, or transparently
    // resumes an existing IN_PROGRESS one — the backend decides which, derived entirely from the
    // caller's PlacementProfile, never from a client-supplied assessment/role id.
    @PostMapping("/attempts")
    public ResponseEntity<PlacementAttemptDetailResponse> start(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attemptService.startOrResume(principal.getId()));
    }

    // Returns PlacementAttemptDetailResponse while IN_PROGRESS, PlacementAttemptReviewResponse
    // once terminal — see PlacementAttemptService.getAttempt.
    @GetMapping("/attempts/{attemptId}")
    public Object getAttempt(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID attemptId) {
        return attemptService.getAttempt(principal.getId(), attemptId);
    }

    @PatchMapping("/attempts/{attemptId}/questions/{attemptQuestionId}/answer")
    public PlacementAnswerResponse saveAnswer(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attemptId, @PathVariable UUID attemptQuestionId,
            @Valid @RequestBody PlacementAnswerRequest request) {
        return attemptService.saveAnswer(principal.getId(), attemptId, attemptQuestionId, request.selectedOptionIds());
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public PlacementAttemptReviewResponse submit(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attemptId) {
        return attemptService.submit(principal.getId(), attemptId);
    }

    // The scored, skill-broken-down, gap-ranked summary. 409s (via PlacementReadinessService) if
    // the attempt is still IN_PROGRESS.
    @GetMapping("/attempts/{attemptId}/result")
    public PlacementReadinessResultResponse getResult(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attemptId) {
        return readinessService.getResult(principal.getId(), attemptId);
    }

    // "Latest" = most recently submitted, across any attempt this student has made. 204 (not 404)
    // when the student has never completed one — a normal empty state, not an error.
    @GetMapping("/latest-result")
    public ResponseEntity<PlacementReadinessResultResponse> latestResult(@AuthenticationPrincipal UserPrincipal principal) {
        return readinessService.latestForStudent(principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
