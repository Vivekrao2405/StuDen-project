package com.studen.practical;

import com.studen.integrity.HeartbeatRequest;
import com.studen.integrity.IntegrityEventBatchRequest;
import com.studen.integrity.IntegrityEventService;
import com.studen.integrity.IntegritySummaryResponse;
import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/practical-attempts")
public class PracticalAttemptController {

    private final PracticalAttemptService attemptService;
    private final IntegrityEventService integrityEventService;

    public PracticalAttemptController(PracticalAttemptService attemptService, IntegrityEventService integrityEventService) {
        this.attemptService = attemptService;
        this.integrityEventService = integrityEventService;
    }

    // Returns PracticalAttemptResponse while IN_PROGRESS, PracticalAttemptResultResponse once
    // terminal — see PracticalAttemptService.getAttempt.
    @GetMapping("/{id}")
    public Object get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return attemptService.getAttempt(principal.getId(), id);
    }

    @PostMapping("/{id}/submit")
    public PracticalAttemptResultResponse submit(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return attemptService.submit(principal.getId(), id);
    }

    // Phase 7.6 — {questionId} here is a PracticalAttemptQuestion id (one entry from the `questions`
    // array on PracticalAttemptResponse), already scoped to this attempt.
    @PatchMapping("/{id}/questions/{questionId}")
    public PracticalAttemptResponse save(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID questionId, @Valid @RequestBody SaveAttemptRequest request) {
        return attemptService.saveProgress(principal.getId(), id, questionId, request);
    }

    // CODING/SQL only — real sandboxed execution against public test cases (Phase 7.5), scoped to
    // one question. Returns 503 (com.studen.practical.execution.ExecutionServiceUnavailableException)
    // rather than a fake 200 result if the execution infrastructure itself is unreachable; never
    // fabricates pass/fail counts. See GET /api/v1/execution-status to check availability up front.
    @PostMapping("/{id}/questions/{questionId}/run")
    public RunResultResponse run(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID questionId) {
        return attemptService.run(principal.getId(), id, questionId);
    }

    // Run #1, #2, #3... — every execution recorded for this question, oldest first.
    @GetMapping("/{id}/questions/{questionId}/executions")
    public List<ExecutionJobSummaryResponse> executions(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @PathVariable UUID questionId) {
        return attemptService.executionHistory(principal.getId(), id, questionId);
    }

    // Phase 7.6 Assessment Integrity — batched behavioral signals from the attempt workspace
    // (tab visibility, copy/paste/cut, fullscreen). Never trusts client-supplied severity; see
    // IntegrityEventService. Integrity stays attempt-wide (not per-question) — the frontend may
    // include the active question id in an event's opaque `metadata` string for display purposes
    // only, never interpreted server-side.
    @PostMapping("/{id}/integrity-events")
    public IntegritySummaryResponse recordIntegrityEvents(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody IntegrityEventBatchRequest request) {
        return integrityEventService.recordBatch(principal.getId(), id, request.events());
    }

    // Lightweight per-tab presence signal — feeds multiple-active-session detection. Purely
    // additive: never affects the attempt timer/expiry, which stays governed by `deadline` alone.
    @PostMapping("/{id}/heartbeat")
    public IntegritySummaryResponse heartbeat(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody HeartbeatRequest request) {
        return integrityEventService.heartbeat(principal.getId(), id, request.sessionId());
    }

    // Coarse, neutral own-attempt summary — no deduction values or thresholds (goal #20).
    @GetMapping("/{id}/integrity-summary")
    public IntegritySummaryResponse integritySummary(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return integrityEventService.getSummary(principal.getId(), id);
    }
}
