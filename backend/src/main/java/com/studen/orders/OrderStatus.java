package com.studen.orders;

// A WorkOrder is only ever created once its ServiceRequest is ACCEPTED (see
// OrderService.getOrCreateOrder), and starts life directly at IN_PROGRESS — there is no separate
// "accepted but not started" state on this entity, so cancellation only ever needs to guard
// IN_PROGRESS -> CANCELLED.
public enum OrderStatus {
    IN_PROGRESS,
    WORK_SUBMITTED,
    COMPLETED,
    CANCELLED
}
