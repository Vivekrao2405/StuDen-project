package com.studen.resource;

import com.studen.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// No class-level @PreAuthorize — relies on the default authenticated-by-default security config,
// same posture as PortfolioController. "/my-learning" is a literal segment so it's matched before
// the "/{id}" path variable (standard Spring MVC precedence), same as any other REST resource.
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/my-learning")
    public MyLearningResponse myLearning(@AuthenticationPrincipal UserPrincipal principal) {
        return resourceService.myLearning(principal.getId());
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return resourceService.get(principal.getId(), id);
    }

    // Inline viewing — used by the resource-detail PDF viewer (fetched as a Blob, never navigated
    // to directly, since this endpoint requires the same Bearer auth as every other route here).
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> viewFile(@PathVariable UUID id) {
        return resourceService.file(id, false);
    }

    // Explicit download — the only path that sends Content-Disposition: attachment.
    @GetMapping("/{id}/file/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID id) {
        return resourceService.file(id, true);
    }

    @PostMapping("/{id}/start")
    public ResourceProgressResponse start(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return resourceService.start(principal.getId(), id);
    }

    @PostMapping("/{id}/complete")
    public ResourceProgressResponse complete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return resourceService.complete(principal.getId(), id);
    }
}
