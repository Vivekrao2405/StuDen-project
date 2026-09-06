package com.studen.placement;

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

/**
 * Admin configuration of role-specific readiness assessments. Configuration only: there is no
 * start/submit endpoint in Phase 0, because the assessment engine itself belongs to Phase 3.
 */
@RestController
@RequestMapping("/api/v1/admin/placement/assessments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlacementAssessmentController {

    private final PlacementAssessmentService service;

    public AdminPlacementAssessmentController(PlacementAssessmentService service) {
        this.service = service;
    }

    @GetMapping
    public PlacementPageResponse<PlacementAssessmentResponse> list(
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) PlacementContentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(roleId, status, search, page, size);
    }

    @GetMapping("/{id}")
    public PlacementAssessmentDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PlacementAssessmentDetailResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PlacementAssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public PlacementAssessmentDetailResponse update(@PathVariable UUID id,
            @Valid @RequestBody PlacementAssessmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public PlacementAssessmentDetailResponse publish(@PathVariable UUID id) {
        return service.setStatus(id, PlacementContentStatus.PUBLISHED);
    }

    @PostMapping("/{id}/unpublish")
    public PlacementAssessmentDetailResponse unpublish(@PathVariable UUID id) {
        return service.setStatus(id, PlacementContentStatus.DRAFT);
    }

    @PostMapping("/{id}/archive")
    public PlacementAssessmentDetailResponse archive(@PathVariable UUID id) {
        return service.setStatus(id, PlacementContentStatus.ARCHIVED);
    }

    // --- Configured questions --------------------------------------------------------------

    @PostMapping("/{id}/questions")
    public ResponseEntity<PlacementAssessmentQuestionResponse> addQuestion(@PathVariable UUID id,
            @Valid @RequestBody PlacementAssessmentQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addQuestion(id, request));
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    public ResponseEntity<Void> removeQuestion(@PathVariable UUID id, @PathVariable UUID questionId) {
        service.removeQuestion(id, questionId);
        return ResponseEntity.noContent().build();
    }
}
