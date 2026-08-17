package com.studen.practical;

import com.studen.questionbank.Difficulty;
import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Every method here requires ADMIN — mirrors AdminQuestionController's class-level
// @PreAuthorize convention exactly (the only prior admin-content-management precedent).
@RestController
@RequestMapping("/api/v1/admin/practical-assessments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPracticalAssessmentController {

    private final AdminPracticalAssessmentService service;

    public AdminPracticalAssessmentController(AdminPracticalAssessmentService service) {
        this.service = service;
    }

    @GetMapping
    public PracticalPageResponse<PracticalAssessmentSummaryResponse> list(
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) PracticalType practicalType,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) PracticalAssessmentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(skillId, practicalType, difficulty, status, search, page, size);
    }

    @GetMapping("/{id}")
    public PracticalAssessmentDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PracticalAssessmentDetailResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PracticalAssessmentRequest request) {
        PracticalAssessmentDetailResponse response = service.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public PracticalAssessmentDetailResponse update(@PathVariable UUID id, @Valid @RequestBody PracticalAssessmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit-review")
    public PracticalAssessmentDetailResponse submitForReview(@PathVariable UUID id) {
        return service.submitForReview(id);
    }

    @PostMapping("/{id}/publish")
    public PracticalAssessmentDetailResponse publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return service.publish(id, principal.getId());
    }

    @PostMapping("/{id}/archive")
    public PracticalAssessmentDetailResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/new-version")
    public ResponseEntity<PracticalAssessmentDetailResponse> createNewVersion(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PracticalAssessmentDetailResponse response = service.createNewVersion(id, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
