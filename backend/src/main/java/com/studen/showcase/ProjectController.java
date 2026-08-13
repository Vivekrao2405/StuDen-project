package com.studen.showcase;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/v1/users/me/projects")
public class ProjectController {

    // Temporary [PERF] timing logs (development-only signal, not a permanent metrics pipeline) —
    // requested to measure where Showcase save/upload latency actually goes: controller+service+
    // DB vs. the external Cloudinary call. Logs a duration only, never request/file contents.
    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> getMyProjects(@AuthenticationPrincipal UserPrincipal principal) {
        long start = System.nanoTime();
        List<ProjectResponse> response = projectService.listMyProjects(principal.getId());
        log.info("[PERF] GET /users/me/projects ({} projects) = {} ms",
                response.size(), elapsedMs(start));
        return response;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProjectRequest request) {
        long start = System.nanoTime();
        ProjectResponse response = projectService.createProject(principal.getId(), request);
        log.info("[PERF] POST /users/me/projects = {} ms", elapsedMs(start));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request) {
        long start = System.nanoTime();
        ProjectResponse response = projectService.updateProject(principal.getId(), projectId, request);
        log.info("[PERF] PUT /users/me/projects/{id} = {} ms", elapsedMs(start));
        return response;
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId) {
        projectService.deleteProject(principal.getId(), projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/media")
    public ProjectMediaResponse uploadMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @RequestParam("file") MultipartFile file) {
        long start = System.nanoTime();
        ProjectMediaResponse response = projectService.uploadMedia(principal.getId(), projectId, file);
        log.info("[PERF] POST /users/me/projects/{{id}}/media ({} bytes) = {} ms",
                file.getSize(), elapsedMs(start));
        return response;
    }

    @DeleteMapping("/{projectId}/media/{mediaId}")
    public ProjectResponse removeMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @PathVariable UUID mediaId) {
        return projectService.removeMedia(principal.getId(), projectId, mediaId);
    }

    @PutMapping("/{projectId}/media/order")
    public ProjectResponse reorderMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @Valid @RequestBody UpdateMediaOrderRequest request) {
        return projectService.reorderMedia(principal.getId(), projectId, request);
    }

    @PutMapping("/{projectId}/media/{mediaId}/cover")
    public ProjectResponse setCoverMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @PathVariable UUID mediaId) {
        return projectService.setCoverMedia(principal.getId(), projectId, mediaId);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
