package com.studen.practical;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ADMIN-only "Test Question" tool -- mirrors AdminPracticalAssessmentController's class-level
// @PreAuthorize convention. Never touches a PracticalAttempt; nothing here is persisted.
@RestController
@RequestMapping("/api/v1/admin/practical-assessments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExecutionController {

    private final AdminExecutionService service;

    public AdminExecutionController(AdminExecutionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/test-run")
    public AdminTestRunResponse testRun(@PathVariable UUID id, @Valid @RequestBody AdminTestRunRequest request) {
        return service.testRun(id, request);
    }
}
