package com.studen.placement;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller own placement profile. Always scoped to the authenticated principal — there is no
 * path variable for a user id, so one student can never read or write another profile.
 *
 * <p>GET returns 204 when the student has not onboarded yet: no profile is a normal state, not an
 * error. PUT upserts, which is what stops a returning student from being pushed back through
 * onboarding.
 */
@RestController
@RequestMapping("/api/v1/placement/profile")
public class PlacementProfileController {

    private final PlacementProfileService service;

    public PlacementProfileController(PlacementProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PlacementProfileResponse> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return service.findMyProfile(principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public PlacementProfileResponse saveMyProfile(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PlacementProfileRequest request) {
        return service.save(principal.getId(), request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        service.deleteMyProfile(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
