/**
 * The post-acceptance work lifecycle: one {@link com.studen.orders.WorkOrder} per ACCEPTED
 * service request, carrying it through IN_PROGRESS -> WORK_SUBMITTED -> COMPLETED, or CANCELLED.
 */
package com.studen.orders;
