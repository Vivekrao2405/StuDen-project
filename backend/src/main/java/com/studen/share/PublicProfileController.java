package com.studen.share;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles")
public class PublicProfileController {

    private final ShareService shareService;

    public PublicProfileController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{slug}")
    public PublicProfileResponse getPublicProfile(@PathVariable String slug) {
        return shareService.getPublicProfile(slug);
    }
}
