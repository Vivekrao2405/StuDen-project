package com.studen.skill;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Admin management of the shared skill catalog. Authorization mirrors every other admin
 * controller in this codebase: class-level {@code @PreAuthorize("hasRole('ADMIN')")}, no separate
 * permission system.
 */
@RestController
@RequestMapping("/api/v1/admin/skills")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSkillController {

    private final AdminSkillService service;

    public AdminSkillController(AdminSkillService service) {
        this.service = service;
    }

    @GetMapping
    public SkillPageResponse<AdminSkillResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(search, category, page, size);
    }

    @GetMapping("/{id}")
    public AdminSkillResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<AdminSkillResponse> create(@Valid @RequestBody CreateSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminSkillResponse update(@PathVariable UUID id, @Valid @RequestBody CreateSkillRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
