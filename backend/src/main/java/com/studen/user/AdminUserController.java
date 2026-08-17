package com.studen.user;

import com.studen.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Every method here requires ADMIN — a STUDENT calling any of these gets 403 via
// JwtAccessDeniedHandler (already wired app-wide), enforced server-side regardless of what the
// frontend shows. Mirrors AdminQuestionController's class-level @PreAuthorize convention (the
// only prior admin-gated surface in the codebase).
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserPageResponse<AdminUserSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.list(search, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponse get(@PathVariable UUID id) {
        return adminUserService.get(id);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        adminUserService.deactivate(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        adminUserService.restore(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody DeleteUserRequest request) {
        adminUserService.permanentlyDelete(principal.getId(), id, request.confirmation());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
