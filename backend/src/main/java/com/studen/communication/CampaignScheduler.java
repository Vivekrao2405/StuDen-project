package com.studen.communication;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls once a minute for SCHEDULED campaigns whose time has come. {@link
 * CommunicationCampaignRepository#claimForProcessing} is an atomic conditional UPDATE
 * (SCHEDULED -> PROCESSING, WHERE status = SCHEDULED) — that single statement is the entire
 * idempotency guarantee: if this tick's claim returns 0, a previous tick (or, in the future, a
 * second app instance) already has it, so nothing here proceeds to resolve/queue/send twice. Two
 * campaigns are otherwise handled fully independently, so one throwing never blocks the others in
 * the same tick.
 */
@Component
public class CampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampaignScheduler.class);

    private final CommunicationCampaignRepository campaignRepository;
    private final CampaignSendService campaignSendService;

    public CampaignScheduler(CommunicationCampaignRepository campaignRepository,
            CampaignSendService campaignSendService) {
        this.campaignRepository = campaignRepository;
        this.campaignSendService = campaignSendService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void processDueCampaigns() {
        Instant now = Instant.now();
        List<CommunicationCampaign> due = campaignRepository
                .findAllByStatusAndScheduledAtLessThanEqual(CampaignStatus.SCHEDULED, now);
        for (CommunicationCampaign campaign : due) {
            try {
                // Spring Data JPA repository proxies wrap every declared method (including custom
                // @Modifying @Query ones) in their own transaction automatically, so this atomic
                // claim is transactional even though nothing here opens one explicitly.
                if (campaignRepository.claimForProcessing(campaign.getId(), now) == 1) {
                    campaignSendService.resolveAndQueueClaimed(campaign.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process scheduled campaign {}", campaign.getId(), e);
            }
        }
    }
}
