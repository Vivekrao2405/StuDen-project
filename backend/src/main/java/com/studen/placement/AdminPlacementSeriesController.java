package com.studen.placement;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
 * Admin authoring of Placement Prep content. Series, modules and module items all live under one
 * controller because modules and items are only ever addressed through the series they belong to.
 *
 * <p>No student-facing prep endpoint exists in Phase 0 — this is content structure only.
 */
@RestController
@RequestMapping("/api/v1/admin/placement/series")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlacementSeriesController {

    private final PlacementSeriesService service;

    public AdminPlacementSeriesController(PlacementSeriesService service) {
        this.service = service;
    }

    // --- Series ----------------------------------------------------------------------------

    @GetMapping
    public PlacementPageResponse<PlacementSeriesResponse> list(
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) PlacementContentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listSeries(roleId, companyId, companyType, status, search, page, size);
    }

    @GetMapping("/{id}")
    public PlacementSeriesDetailResponse get(@PathVariable UUID id) {
        return service.getSeries(id);
    }

    @PostMapping
    public ResponseEntity<PlacementSeriesDetailResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PlacementSeriesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSeries(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public PlacementSeriesDetailResponse update(@PathVariable UUID id,
            @Valid @RequestBody PlacementSeriesRequest request) {
        return service.updateSeries(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteSeries(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public PlacementSeriesDetailResponse publish(@PathVariable UUID id) {
        return service.setSeriesStatus(id, PlacementContentStatus.PUBLISHED);
    }

    @PostMapping("/{id}/unpublish")
    public PlacementSeriesDetailResponse unpublish(@PathVariable UUID id) {
        return service.setSeriesStatus(id, PlacementContentStatus.DRAFT);
    }

    @PostMapping("/{id}/archive")
    public PlacementSeriesDetailResponse archive(@PathVariable UUID id) {
        return service.setSeriesStatus(id, PlacementContentStatus.ARCHIVED);
    }

    // --- Modules ---------------------------------------------------------------------------

    @GetMapping("/{id}/modules")
    public List<PlacementModuleResponse> listModules(@PathVariable UUID id) {
        return service.listModules(id);
    }

    @PostMapping("/{id}/modules")
    public ResponseEntity<PlacementModuleResponse> createModule(@PathVariable UUID id,
            @Valid @RequestBody PlacementModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createModule(id, request));
    }

    @PutMapping("/{id}/modules/reorder")
    public List<PlacementModuleResponse> reorderModules(@PathVariable UUID id,
            @Valid @RequestBody ReorderRequest request) {
        return service.reorderModules(id, request);
    }

    @PutMapping("/modules/{moduleId}")
    public PlacementModuleResponse updateModule(@PathVariable UUID moduleId,
            @Valid @RequestBody PlacementModuleRequest request) {
        return service.updateModule(moduleId, request);
    }

    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<Void> deleteModule(@PathVariable UUID moduleId) {
        service.deleteModule(moduleId);
        return ResponseEntity.noContent().build();
    }

    // --- Module items ----------------------------------------------------------------------

    @GetMapping("/modules/{moduleId}/items")
    public List<PlacementModuleItemResponse> listModuleItems(@PathVariable UUID moduleId) {
        return service.listModuleItems(moduleId);
    }

    @PostMapping("/modules/{moduleId}/items")
    public ResponseEntity<PlacementModuleItemResponse> addModuleItem(@PathVariable UUID moduleId,
            @Valid @RequestBody PlacementModuleItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addModuleItem(moduleId, request));
    }

    @PutMapping("/modules/{moduleId}/items/reorder")
    public List<PlacementModuleItemResponse> reorderModuleItems(@PathVariable UUID moduleId,
            @Valid @RequestBody ReorderRequest request) {
        return service.reorderModuleItems(moduleId, request);
    }

    @DeleteMapping("/modules/items/{itemId}")
    public ResponseEntity<Void> removeModuleItem(@PathVariable UUID itemId) {
        service.removeModuleItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
