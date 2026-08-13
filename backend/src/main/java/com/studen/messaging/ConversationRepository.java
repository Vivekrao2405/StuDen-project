package com.studen.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    // Every read here JOIN FETCHes requester/provider/serviceRequest: ConversationService builds
    // response DTOs from whatever these queries return, and unlike LAZY (which would hand back
    // uninitialized proxies that throw LazyInitializationException once this query's own short
    // transaction closes — see ConversationService's class-level comment for why these methods
    // are deliberately NOT wrapped in one outer @Transactional), a JOIN FETCH loads real,
    // already-populated objects that remain safely readable afterward.
    @Query("""
            select c from Conversation c
            join fetch c.requester join fetch c.provider join fetch c.serviceRequest
            where c.serviceRequest.id = :serviceRequestId
            """)
    Optional<Conversation> findByServiceRequestId(@Param("serviceRequestId") UUID serviceRequestId);

    // Explicit @Query, not a derived findByIdAndRequesterIdOrProviderId name — see
    // ServiceRequestRepository's identically-named method for why a derived name here would parse
    // as (id = ? AND requesterId = ?) OR providerId = ?, silently dropping the id filter from the
    // second branch (the exact bug fixed in Phase 6.6).
    @Query("""
            select c from Conversation c
            join fetch c.requester join fetch c.provider join fetch c.serviceRequest
            where c.id = :id and (c.requester.id = :userId or c.provider.id = :userId)
            """)
    Optional<Conversation> findByIdAndRequesterIdOrProviderId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select c from Conversation c
            join fetch c.requester join fetch c.provider join fetch c.serviceRequest
            where c.requester.id = :userId or c.provider.id = :userId
            """)
    List<Conversation> findAllByRequesterIdOrProviderId(@Param("userId") UUID userId);
}
