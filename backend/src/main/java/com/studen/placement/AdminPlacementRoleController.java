package com.studen.placement;

import jakarta.validation.Valid;
import java.util.List;
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
 * Admin management of placement roles and their required-skill mappings.
 *
 * <p>Authorization reuses the existing architecture unchanged: class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")}, the same convention every other admin controller in
 * this codebase uses. The spec PLACEMENT_* permission set belongs to a later phase.
 */
@RestController
@RequestMapping("/api/v1/admin/placement/roles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlacementRoleController {

    private final PlacementRoleService service;

    public AdminPlacementRoleController(PlacementRoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlacementRoleResponse> list(@RequestParam(required = false) PlacementCatalogStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public PlacementRoleDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PlacementRoleDetailResponse> create(@Valid @RequestBody PlacementRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public PlacementRoleDetailResponse update(@PathVariable UUID id,
            @Valid @RequestBody PlacementRoleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public PlacementRoleDetailResponse activate(@PathVariable UUID id) {
        return service.setStatus(id, PlacementCatalogStatus.ACTIVE);
    }

    @PostMapping("/{id}/deactivate")
    public PlacementRoleDetailResponse deactivate(@PathVariable UUID id) {
        return service.setStatus(id, PlacementCatalogStatus.INACTIVE);
    }

    // --- Role -> Skill mapping -------------------------------------------------------------

    @GetMapping("/{id}/skills")
    public List<RoleSkillResponse> listSkills(@PathVariable UUID id) {
        return service.listRoleSkills(id);
    }

    @PutMapping("/{id}/skills")
    public List<RoleSkillResponse> replaceSkills(@PathVariable UUID id,
            @Valid @RequestBody RoleSkillsRequest request) {
        return service.replaceRoleSkills(id, request);
    }

    @PostMapping("/{id}/skills")
    public ResponseEntity<RoleSkillResponse> addSkill(@PathVariable UUID id,
            @Valid @RequestBody RoleSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addRoleSkill(id, request));
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    public ResponseEntity<Void> removeSkill(@PathVariable UUID id, @PathVariable UUID skillId) {
        service.removeRoleSkill(id, skillId);
        return ResponseEntity.noContent().build();
    }
}
