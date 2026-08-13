package com.studen.booking;

import com.studen.common.entity.BaseEntity;
import com.studen.marketplace.ServiceCurrency;
import com.studen.marketplace.ServiceListing;
import com.studen.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student requesting another student's {@link ServiceListing} (e.g. "I need a dashboard for my
 * college project..."). Deliberately named "ServiceRequest" per the product spec even though
 * {@code com.studen.marketplace.ServiceRequest} already exists (the create/update DTO for a
 * listing, an unrelated concept) — the two live in different packages so there's no compile
 * collision, only an import-time one, which no single file needs to do.
 *
 * <p>{@code service} is nullable ({@code ON DELETE SET NULL}, see V9 migration) because
 * {@code ServiceListingService.deleteService} really does hard-delete a listing; the
 * {@code serviceTitleSnapshot}/{@code servicePriceSnapshot}/{@code serviceCurrencySnapshot}
 * fields keep the request meaningful to both parties even if that happens, or if the provider
 * edits the listing's price after the fact.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_requests")
public class ServiceRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceListing service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // Always derived server-side from service.getPortfolio().getUser() at creation time — never
    // accepted from the client. See ServiceRequestService.createRequest.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(name = "service_title_snapshot", nullable = false)
    private String serviceTitleSnapshot;

    @Column(name = "service_price_snapshot")
    private Integer servicePriceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_currency_snapshot")
    private ServiceCurrency serviceCurrencySnapshot;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    // Stored separately from the service's listed price — never overwrites ServiceListing.priceAmount.
    @Column(name = "proposed_budget")
    private Integer proposedBudget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceRequestStatus status = ServiceRequestStatus.PENDING;

    // Set only by ServiceRequestRepository.acceptIfPending/rejectIfPending's conditional UPDATE —
    // never both populated, since PENDING -> ACCEPTED and PENDING -> REJECTED are the only two
    // reachable transitions this phase and each is a one-way door out of PENDING.
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "service_request_links", joinColumns = @JoinColumn(name = "service_request_id"))
    @OrderColumn(name = "link_order")
    private List<ServiceRequestLink> links = new ArrayList<>();

    public ServiceRequest(ServiceListing service, User requester, User provider, String description) {
        this.service = service;
        this.requester = requester;
        this.provider = provider;
        this.description = description;
        this.serviceTitleSnapshot = service.getTitle();
        this.servicePriceSnapshot = service.getPriceAmount();
        this.serviceCurrencySnapshot = service.getCurrency();
    }
}
