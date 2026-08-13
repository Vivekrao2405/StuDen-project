package com.studen.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    List<ServiceRequest> findAllByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<ServiceRequest> findAllByProviderIdOrderByCreatedAtDesc(UUID providerId);

    // The ownership-scoped detail lookup — a request is visible to either party, never to anyone
    // else. Mirrors ServiceListingRepository.findByIdAndPortfolioId's "scoped query, not a manual
    // owner check" convention; an id that exists but belongs to neither party returns empty, and
    // the caller maps that straight to a 404.
    Optional<ServiceRequest> findByIdAndRequesterIdOrProviderId(UUID id, UUID requesterId, UUID providerId);

    boolean existsByServiceIdAndRequesterIdAndStatus(UUID serviceId, UUID requesterId, ServiceRequestStatus status);

    long countByRequesterIdAndCreatedAtAfter(UUID requesterId, Instant after);
}
