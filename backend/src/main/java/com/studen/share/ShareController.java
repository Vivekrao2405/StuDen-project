package com.studen.share;

import com.studen.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio/me")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/share")
    public ShareMetadataResponse getMyShareMetadata(@AuthenticationPrincipal UserPrincipal principal) {
        return shareService.getMyShareMetadata(principal.getId());
    }
}
