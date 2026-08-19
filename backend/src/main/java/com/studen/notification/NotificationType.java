package com.studen.notification;

public enum NotificationType {
    NEW_SERVICE_REQUEST,
    REQUEST_ACCEPTED,
    REQUEST_REJECTED,
    NEW_MESSAGE,
    WORK_SUBMITTED,
    ORDER_COMPLETED,
    ORDER_CANCELLED,
    PRACTICAL_ASSESSMENT_EVALUATED,
    // Every in-app notification created by the admin Communications Center (see
    // com.studen.communication.CampaignDeliveryWorker). Unlike every other type here, its URL is
    // never derived from a resourceId — the sending admin specifies the CTA URL directly on the
    // campaign, so NotificationService.notifyForCampaign sets Notification.url itself instead of
    // going through NotificationUrlBuilder.
    ADMIN_MESSAGE
}
