package com.studen.notification;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Instant before) {
        return notificationService.list(principal.getId(), before);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return new UnreadCountResponse(notificationService.unreadCount(principal.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        notificationService.markRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public List<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.getPreferences(principal.getId());
    }

    @PutMapping("/preferences/{type}")
    public NotificationPreferenceResponse updatePreference(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable NotificationType type, @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return notificationService.updatePreference(principal.getId(), type, request);
    }
}
