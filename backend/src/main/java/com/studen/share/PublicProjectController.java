package com.studen.share;

import com.studen.showcase.PublicProjectDetailResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/projects")
public class PublicProjectController {

    private final ShareService shareService;

    public PublicProjectController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{projectId}")
    public PublicProjectDetailResponse getPublicProject(@PathVariable UUID projectId) {
        return shareService.getPublicProject(projectId);
    }
}
