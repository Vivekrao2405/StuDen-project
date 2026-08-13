package com.studen.notification;

public record NotificationPreferenceResponse(NotificationType type, boolean pushEnabled, boolean inAppEnabled) {
}
