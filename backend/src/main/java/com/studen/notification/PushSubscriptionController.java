package com.studen.notification;

import com.studen.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final VapidProperties vapidProperties;

    public PushSubscriptionController(PushSubscriptionService pushSubscriptionService, VapidProperties vapidProperties) {
        this.pushSubscriptionService = pushSubscriptionService;
        this.vapidProperties = vapidProperties;
    }

    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse getVapidPublicKey() {
        // Not secret — every push-enabled site exposes its VAPID public key, since it's meant to
        // be handed to PushManager.subscribe()'s applicationServerKey in the browser. Sits behind
        // normal auth like everything else purely because it's only ever called from the
        // already-authenticated app shell, not because the value itself needs protecting.
        return new VapidPublicKeyResponse(vapidProperties.getPublicKey());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<PushSubscriptionResponse> register(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterPushSubscriptionRequest request, HttpServletRequest httpRequest) {
        PushSubscriptionResponse response = pushSubscriptionService.register(principal.getId(), request,
                httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String endpoint) {
        pushSubscriptionService.unregister(principal.getId(), endpoint);
        return ResponseEntity.noContent().build();
    }
}
