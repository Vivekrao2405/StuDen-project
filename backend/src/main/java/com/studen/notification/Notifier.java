package com.studen.notification;

import java.util.UUID;

/**
 * Integration point every business event notifies through. {@link NotificationService} is the
 * sole production implementation — it persists an in-app {@link Notification} row and dispatches
 * a Web Push, both gated by the recipient's {@link NotificationPreference} for {@code type}.
 * {@code resourceId} is the id of the thing this notification is about (a ServiceRequest, a
 * WorkOrder, or a Conversation) — {@link NotificationUrlBuilder} turns it into the deep link
 * carried in both the in-app row and the push payload.
 */
public interface Notifier {

    void notify(UUID userId, NotificationType type, String message, UUID resourceId);
}
