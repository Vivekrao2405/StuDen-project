package com.studen.resource;

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
import org.springframework.web.multipart.MultipartFile;

// Every method requires ADMIN — mirrors AdminPracticalAssessmentController's class-level
// @PreAuthorize convention exactly.
@RestController
@RequestMapping("/api/v1/admin/resources")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResourceController {

    private final AdminResourceService service;

    public AdminResourceController(AdminResourceService service) {
        this.service = service;
    }

    @GetMapping
    public ResourcePageResponse<ResourceSummaryResponse> list(
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) ResourceType resourceType,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(skillId, resourceType, difficulty, status, search, page, size);
    }

    @GetMapping("/{id}")
    public ResourceDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ResourceDetailResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ResourceRequest request) {
        ResourceDetailResponse response = service.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResourceDetailResponse update(@PathVariable UUID id, @Valid @RequestBody ResourceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ResourceDetailResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/unpublish")
    public ResourceDetailResponse unpublish(@PathVariable UUID id) {
        return service.unpublish(id);
    }

    @PostMapping("/{id}/archive")
    public ResourceDetailResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/upload-file")
    public ResourceDetailResponse uploadFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.uploadFile(id, file);
    }

    @DeleteMapping("/{id}/file")
    public ResourceDetailResponse deleteFile(@PathVariable UUID id) {
        return service.deleteFile(id);
    }
}
