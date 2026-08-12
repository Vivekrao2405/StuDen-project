package com.studen.showcase;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> getMyProjects(@AuthenticationPrincipal UserPrincipal principal) {
        return projectService.listMyProjects(principal.getId());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProjectRequest request) {
        ProjectResponse response = projectService.createProject(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request) {
        return projectService.updateProject(principal.getId(), projectId, request);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId) {
        projectService.deleteProject(principal.getId(), projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/media")
    public ProjectResponse uploadMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId, @RequestParam("file") MultipartFile file) {
        return projectService.uploadMedia(principal.getId(), projectId, file);
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
}
