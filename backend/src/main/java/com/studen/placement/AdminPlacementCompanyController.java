package com.studen.placement;

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

@RestController
@RequestMapping("/api/v1/admin/placement/companies")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlacementCompanyController {

    private final PlacementCompanyService service;

    public AdminPlacementCompanyController(PlacementCompanyService service) {
        this.service = service;
    }

    @GetMapping
    public PlacementPageResponse<PlacementCompanyResponse> list(
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) PlacementCatalogStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(companyType, status, search, page, size);
    }

    @GetMapping("/{id}")
    public PlacementCompanyResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PlacementCompanyResponse> create(@Valid @RequestBody PlacementCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public PlacementCompanyResponse update(@PathVariable UUID id,
            @Valid @RequestBody PlacementCompanyRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public PlacementCompanyResponse activate(@PathVariable UUID id) {
        return service.setStatus(id, PlacementCatalogStatus.ACTIVE);
    }

    @PostMapping("/{id}/deactivate")
    public PlacementCompanyResponse deactivate(@PathVariable UUID id) {
        return service.setStatus(id, PlacementCatalogStatus.INACTIVE);
    }
}
