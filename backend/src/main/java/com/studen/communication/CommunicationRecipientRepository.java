package com.studen.communication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunicationRecipientRepository extends JpaRepository<CommunicationRecipient, UUID> {

    List<CommunicationRecipient> findAllByCampaignIdAndStatus(UUID campaignId, RecipientStatus status);

    List<CommunicationRecipient> findAllByCampaignId(UUID campaignId);

    long countByCampaignId(UUID campaignId);

    long countByCampaignIdAndChannelAndStatusIn(UUID campaignId, RecipientChannel channel, List<RecipientStatus> statuses);

    boolean existsByCampaignIdAndStatus(UUID campaignId, RecipientStatus status);

    // Resets only FAILED rows back to QUEUED for a retry — SENT/DELIVERED/BOUNCED/COMPLAINED/
    // SKIPPED rows are never touched, so a retry can never re-send an already-successful delivery.
    @Modifying(clearAutomatically = true)
    @Query("""
            update CommunicationRecipient r
            set r.status = com.studen.communication.RecipientStatus.QUEUED, r.errorMessage = null
            where r.campaign.id = :campaignId and r.status = com.studen.communication.RecipientStatus.FAILED
            """)
    int requeueFailed(@Param("campaignId") UUID campaignId);

    Optional<CommunicationRecipient> findByProviderMessageId(String providerMessageId);
}
