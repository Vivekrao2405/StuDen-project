package com.studen.communication;

import com.studen.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Every method requires ADMIN — a STUDENT calling any of these gets 403 via
// JwtAccessDeniedHandler (app-wide), matching AdminUserController's class-level convention.
@RestController
@RequestMapping("/api/v1/admin/communications/campaigns")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommunicationCampaignController {

    private final CommunicationService communicationService;
    private final CampaignSendService campaignSendService;
    private final CommunicationAnalyticsService analyticsService;

    public AdminCommunicationCampaignController(CommunicationService communicationService,
            CampaignSendService campaignSendService, CommunicationAnalyticsService analyticsService) {
        this.communicationService = communicationService;
        this.campaignSendService = campaignSendService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public CampaignPageResponse<CampaignSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return communicationService.listCampaigns(page, size);
    }

    @GetMapping("/{id}")
    public CampaignDetailResponse get(@PathVariable UUID id) {
        return communicationService.getCampaign(id);
    }

    @PostMapping
    public CampaignDetailResponse create(@AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CampaignRequest request) {
        return communicationService.createCampaign(principal.getId(), request);
    }

    @PatchMapping("/{id}")
    public CampaignDetailResponse update(@PathVariable UUID id, @RequestBody CampaignRequest request) {
        return communicationService.updateCampaign(id, request);
    }

    // Recipient count/sample always come from the backend (AudienceService), never computed or
    // fabricated client-side — this is the endpoint the wizard's "Estimated audience" pill calls.
    @PostMapping("/audience-preview")
    public AudiencePreviewResponse previewAudience(@RequestBody AudiencePreviewRequest request) {
        return communicationService.previewAudience(request.filterJson(), request.isMarketing());
    }

    // 204, not 202 — matches this codebase's frontend apiFetch client, which only special-cases
    // status 204 as "no body to parse" (a 202 with an empty body would otherwise throw trying to
    // parse JSON from nothing). The work is still genuinely asynchronous either way; this is
    // purely about which empty-body status the existing client convention expects.
    @PostMapping("/{id}/send-now")
    public ResponseEntity<Void> sendNow(@PathVariable UUID id) {
        campaignSendService.sendNow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<Void> schedule(@PathVariable UUID id, @RequestBody ScheduleCampaignRequest request) {
        campaignSendService.schedule(id, request.scheduledAt());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        communicationService.cancelCampaign(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry-failed")
    public ResponseEntity<Void> retryFailed(@PathVariable UUID id) {
        campaignSendService.retryFailed(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/analytics")
    public CampaignAnalyticsResponse analytics(@PathVariable UUID id) {
        return analyticsService.forCampaign(id);
    }
}
