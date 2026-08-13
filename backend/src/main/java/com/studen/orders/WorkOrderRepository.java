package com.studen.orders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    // JOIN FETCHes requester/provider/serviceRequest for the same reason as
    // ConversationRepository.findByServiceRequestId: OrderService.getOrCreateOrder is
    // deliberately not wrapped in one outer @Transactional (see that method's comment), so the
    // response built from this result must not depend on LAZY proxies surviving past this query's
    // own short transaction.
    @Query("""
            select o from WorkOrder o
            join fetch o.requester join fetch o.provider join fetch o.serviceRequest
            where o.serviceRequest.id = :serviceRequestId
            """)
    Optional<WorkOrder> findByServiceRequestId(@Param("serviceRequestId") UUID serviceRequestId);

    // Explicit @Query, not a derived findByIdAndRequesterIdOrProviderId name — a derived name here
    // would parse as (id = ? AND requesterId = ?) OR providerId = ?, the exact And/Or pitfall
    // fixed in ServiceRequestRepository during Phase 6.6 and avoided from the start in
    // ConversationRepository during 6.7.
    @Query("""
            select o from WorkOrder o
            join fetch o.requester join fetch o.provider join fetch o.serviceRequest
            where o.id = :id and (o.requester.id = :userId or o.provider.id = :userId)
            """)
    Optional<WorkOrder> findByIdAndRequesterIdOrProviderId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select o from WorkOrder o
            join fetch o.requester join fetch o.provider join fetch o.serviceRequest
            where o.requester.id = :userId or o.provider.id = :userId
            """)
    List<WorkOrder> findAllByRequesterIdOrProviderId(@Param("userId") UUID userId);

    // Role-scoped ownership checks for submit/complete — a plain two-field AND, no Or involved,
    // so a derived name is safe here (unlike the methods above).
    Optional<WorkOrder> findByIdAndProviderId(UUID id, UUID providerId);

    Optional<WorkOrder> findByIdAndRequesterId(UUID id, UUID requesterId);

    // Atomic compare-and-set, mirroring ServiceRequestRepository.acceptIfPending/rejectIfPending:
    // the WHERE clause re-checks the expected status at the database level, so two concurrent
    // transitions on the same order (e.g. "submit" racing "cancel") can never both succeed.
    @Modifying(clearAutomatically = true)
    @Query("""
            update WorkOrder o
            set o.status = com.studen.orders.OrderStatus.WORK_SUBMITTED, o.submittedAt = :now,
                o.submissionDescription = :description, o.submissionLink = :link
            where o.id = :id and o.status = com.studen.orders.OrderStatus.IN_PROGRESS
            """)
    int submitIfInProgress(@Param("id") UUID id, @Param("now") Instant now,
            @Param("description") String description, @Param("link") String link);

    @Modifying(clearAutomatically = true)
    @Query("""
            update WorkOrder o
            set o.status = com.studen.orders.OrderStatus.COMPLETED, o.completedAt = :now
            where o.id = :id and o.status = com.studen.orders.OrderStatus.WORK_SUBMITTED
            """)
    int completeIfSubmitted(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update WorkOrder o
            set o.status = com.studen.orders.OrderStatus.CANCELLED, o.cancelledAt = :now, o.cancellationReason = :reason
            where o.id = :id and o.status = com.studen.orders.OrderStatus.IN_PROGRESS
            """)
    int cancelIfInProgress(@Param("id") UUID id, @Param("now") Instant now, @Param("reason") String reason);
}
