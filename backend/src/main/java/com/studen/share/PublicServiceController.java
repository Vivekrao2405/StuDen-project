package com.studen.share;

import com.studen.marketplace.PublicServiceDetailResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/services")
public class PublicServiceController {

    private final ShareService shareService;

    public PublicServiceController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{serviceId}")
    public PublicServiceDetailResponse getPublicService(@PathVariable UUID serviceId) {
        return shareService.getPublicService(serviceId);
    }
}
