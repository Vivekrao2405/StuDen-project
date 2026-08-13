package com.studen.notification;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(@NotNull Boolean pushEnabled, @NotNull Boolean inAppEnabled) {
}
