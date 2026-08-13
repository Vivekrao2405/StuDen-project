package com.studen.orders;

import com.studen.booking.ServiceRequest;
import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The work-lifecycle counterpart of an ACCEPTED {@link ServiceRequest} — created at most once per
 * request (unique FK on {@code service_request_id}, same shape as {@code Conversation}). It
 * deliberately does not duplicate the request's title/price/budget/delivery-date/description:
 * those were already frozen as snapshot fields on {@link ServiceRequest} at request-creation time
 * and never mutate afterward, so this entity reads them through the {@code serviceRequest}
 * association instead of copying them again.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "work_orders")
public class WorkOrder extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false, unique = true)
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.IN_PROGRESS;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submission_description", columnDefinition = "TEXT")
    private String submissionDescription;

    @Column(name = "submission_link")
    private String submissionLink;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    public WorkOrder(ServiceRequest serviceRequest, User provider, User requester) {
        this.serviceRequest = serviceRequest;
        this.provider = provider;
        this.requester = requester;
    }
}
