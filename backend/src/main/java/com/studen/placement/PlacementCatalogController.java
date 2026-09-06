package com.studen.placement;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog for any authenticated user: the roles a student can target, the skills each
 * role requires, and the companies they can pick from.
 *
 * <p>This exists in Phase 0 specifically so no frontend ever hard-codes a role list, a role skill
 * list, or a company list. Only ACTIVE rows are returned — deactivated catalog entries stay
 * visible to admins through the admin endpoints but never to students.
 */
@RestController
@RequestMapping("/api/v1/placement")
public class PlacementCatalogController {

    private final PlacementRoleService roleService;
    private final PlacementCompanyService companyService;

    public PlacementCatalogController(PlacementRoleService roleService, PlacementCompanyService companyService) {
        this.roleService = roleService;
        this.companyService = companyService;
    }

    @GetMapping("/roles")
    public List<PlacementRoleResponse> listRoles() {
        return roleService.list(PlacementCatalogStatus.ACTIVE);
    }

    @GetMapping("/roles/{id}")
    public PlacementRoleDetailResponse getRole(@PathVariable UUID id) {
        return roleService.get(id);
    }

    // Step 4 of onboarding needs the skills of the selected role, which is exactly this query.
    @GetMapping("/roles/{id}/skills")
    public List<RoleSkillResponse> listRoleSkills(@PathVariable UUID id) {
        return roleService.listRoleSkills(id);
    }

    @GetMapping("/companies")
    public PlacementPageResponse<PlacementCompanyResponse> listCompanies(
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return companyService.list(companyType, PlacementCatalogStatus.ACTIVE, search, page, size);
    }
}
