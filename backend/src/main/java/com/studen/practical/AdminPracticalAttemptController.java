package com.studen.practical;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/practical-attempts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPracticalAttemptController {

    private final AdminPracticalAttemptService service;

    public AdminPracticalAttemptController(AdminPracticalAttemptService service) {
        this.service = service;
    }

    @GetMapping
    public PracticalPageResponse<AdminPracticalAttemptSummaryResponse> queue(
            @RequestParam(required = false) PracticalAttemptStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.queue(status, page, size);
    }

    @GetMapping("/{id}")
    public AdminPracticalAttemptDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/evaluate")
    public AdminPracticalAttemptDetailResponse evaluate(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody EvaluateAttemptRequest request) {
        return service.evaluate(id, principal.getId(), request);
    }
}
