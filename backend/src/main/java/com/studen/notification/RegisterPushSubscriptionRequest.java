package com.studen.notification;

import jakarta.validation.constraints.NotBlank;

public record RegisterPushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotBlank String p256dh,
        @NotBlank String auth) {
}
