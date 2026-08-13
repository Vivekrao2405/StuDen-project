package com.studen.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    List<ServiceRequest> findAllByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<ServiceRequest> findAllByProviderIdOrderByCreatedAtDesc(UUID providerId);

    // The ownership-scoped detail lookup — a request is visible to either party, never to anyone
    // else. Mirrors ServiceListingRepository.findByIdAndPortfolioId's "scoped query, not a manual
    // owner check" convention; an id that exists but belongs to neither party returns empty, and
    // the caller maps that straight to a 404.
    //
    // Explicit @Query, not a derived method name: Spring Data parses "And"/"Or" in derived query
    // names left-to-right with no implicit grouping, so a would-be
    // findByIdAndRequesterIdOrProviderId(id, requesterId, providerId) actually executes as
    // (id = ? AND requesterId = ?) OR providerId = ? — the id filter silently drops out of the
    // second branch, matching every request where the caller is the provider regardless of which
    // id was asked for. Invisible with one request per user; surfaced as a NonUniqueResultException
    // (500) the moment any provider has two.
    @Query("select sr from ServiceRequest sr where sr.id = :id and (sr.requester.id = :requesterId or sr.provider.id = :providerId)")
    Optional<ServiceRequest> findByIdAndRequesterIdOrProviderId(@Param("id") UUID id, @Param("requesterId") UUID requesterId,
            @Param("providerId") UUID providerId);

    // The provider-scoped lookup used by accept/reject — covers "doesn't exist", "you're not the
    // provider", and "you're the requester trying to manage your own request" all at once, exactly
    // like ServiceListingRepository.findOwnService's convention.
    Optional<ServiceRequest> findByIdAndProviderId(UUID id, UUID providerId);

    boolean existsByServiceIdAndRequesterIdAndStatus(UUID serviceId, UUID requesterId, ServiceRequestStatus status);

    long countByRequesterIdAndCreatedAtAfter(UUID requesterId, Instant after);

    // Atomic "compare-and-set": the WHERE clause re-checks status == PENDING at the database level,
    // so two concurrent accept/reject calls on the same request can never both succeed — whichever
    // commits first flips the row, and the second's UPDATE matches zero rows. clearAutomatically
    // evicts the stale in-memory copy so a subsequent findById reflects this write.
    @Modifying(clearAutomatically = true)
    @Query("""
            update ServiceRequest sr
            set sr.status = com.studen.booking.ServiceRequestStatus.ACCEPTED, sr.acceptedAt = :now
            where sr.id = :id and sr.status = com.studen.booking.ServiceRequestStatus.PENDING
            """)
    int acceptIfPending(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update ServiceRequest sr
            set sr.status = com.studen.booking.ServiceRequestStatus.REJECTED, sr.rejectedAt = :now, sr.rejectionReason = :reason
            where sr.id = :id and sr.status = com.studen.booking.ServiceRequestStatus.PENDING
            """)
    int rejectIfPending(@Param("id") UUID id, @Param("now") Instant now, @Param("reason") String reason);
}
