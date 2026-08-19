package com.studen.notification;

import java.util.UUID;

/**
 * Builds the deep-link path for a notification from its type + resourceId, using the frontend's
 * real routes (frontend/src/lib/routes.ts) — never a guessed path. Runs once, server-side, inside
 * {@link NotificationService}, so neither the frontend list view nor the service worker's push
 * handler ever has to reconstruct a URL from type+resourceId themselves.
 */
final class NotificationUrlBuilder {

    private NotificationUrlBuilder() {
    }

    static String build(NotificationType type, UUID resourceId) {
        return switch (type) {
            case NEW_SERVICE_REQUEST, REQUEST_ACCEPTED, REQUEST_REJECTED -> "/requests/" + resourceId;
            case NEW_MESSAGE -> "/messages/" + resourceId;
            case WORK_SUBMITTED, ORDER_COMPLETED, ORDER_CANCELLED -> "/orders/" + resourceId;
            case PRACTICAL_ASSESSMENT_EVALUATED -> "/practical-attempts/" + resourceId + "/result";
            // Never actually reached: campaign notifications are created by
            // NotificationService.notifyForCampaign, which sets Notification.url directly from the
            // admin-specified CTA and never calls this builder. Kept only so this switch stays
            // exhaustive.
            case ADMIN_MESSAGE -> "/notifications";
        };
    }
}
