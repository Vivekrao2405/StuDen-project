package com.studen.practical;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
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

    public PracticalAttemptController(PracticalAttemptService attemptService) {
        this.attemptService = attemptService;
    }

    // Returns PracticalAttemptResponse while IN_PROGRESS, PracticalAttemptResultResponse once
    // terminal — see PracticalAttemptService.getAttempt.
    @GetMapping("/{id}")
    public Object get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return attemptService.getAttempt(principal.getId(), id);
    }

    @PatchMapping("/{id}")
    public PracticalAttemptResponse save(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SaveAttemptRequest request) {
        return attemptService.saveProgress(principal.getId(), id, request);
    }

    @PostMapping("/{id}/submit")
    public PracticalAttemptResultResponse submit(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return attemptService.submit(principal.getId(), id);
    }

    // CODING/SQL only — always returns an honest "not available, saved for manual review" result;
    // never fabricated pass/fail counts (spec §9/§37).
    @PostMapping("/{id}/run")
    public RunResultResponse run(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return attemptService.run(principal.getId(), id);
    }
}
