package com.studen.communication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationCampaignRepository extends JpaRepository<CommunicationCampaign, UUID> {

    Page<CommunicationCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<CommunicationCampaign> findAllByStatusAndScheduledAtLessThanEqual(CampaignStatus status, Instant now);

    // Atomic compare-and-set, same idiom as AssessmentRepository.finalizeIfInProgress — the WHERE
    // re-checks SCHEDULED at the database level so two overlapping scheduler ticks (or, in the
    // future, two app instances) can never both win the race and double-queue the same campaign.
    @Modifying(clearAutomatically = true)
    @Query("""
            update CommunicationCampaign c
            set c.status = com.studen.communication.CampaignStatus.PROCESSING, c.processingStartedAt = :now
            where c.id = :id and c.status = com.studen.communication.CampaignStatus.SCHEDULED
            """)
    int claimForProcessing(@Param("id") UUID id, @Param("now") Instant now);
}
