package com.studen.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String message,
        UUID resourceId,
        String url,
        boolean read,
        Instant createdAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getResourceId(),
                notification.getUrl(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
