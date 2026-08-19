package com.studen.communication;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.communication.audience.AudienceService;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns a DRAFT/SCHEDULED campaign into a resolved audience of {@link CommunicationRecipient}
 * rows and hands off to {@link CampaignDeliveryWorker}. The unique (campaign, user, channel)
 * constraint (V24) is what actually enforces "once per campaign per channel" — this class relies
 * on it rather than re-implementing dedup logic, so it stays correct even under concurrent
 * resolves (a retry re-running resolveAndQueue after a partial failure, for instance).
 */
@Service
public class CampaignSendService {

    private final CommunicationCampaignRepository campaignRepository;
    private final CommunicationRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final AudienceService audienceService;
    private final CampaignDeliveryWorker deliveryWorker;
    private final boolean asyncDeliveryEnabled;

    public CampaignSendService(CommunicationCampaignRepository campaignRepository,
            CommunicationRecipientRepository recipientRepository, UserRepository userRepository,
            AudienceService audienceService, CampaignDeliveryWorker deliveryWorker,
            @Value("${app.communication.async.enabled:true}") boolean asyncDeliveryEnabled) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.userRepository = userRepository;
        this.audienceService = audienceService;
        this.deliveryWorker = deliveryWorker;
        this.asyncDeliveryEnabled = asyncDeliveryEnabled;
    }

    @Transactional
    public void sendNow(UUID campaignId) {
        CommunicationCampaign campaign = findOrThrow(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ConflictException("Only a draft campaign can be sent");
        }
        campaign.setStatus(CampaignStatus.PROCESSING);
        campaign.setProcessingStartedAt(Instant.now());
        campaignRepository.save(campaign);
        resolveAndQueue(campaign);
        triggerDeliveryAfterCommit(campaignId);
    }

    @Transactional
    public void schedule(UUID campaignId, Instant scheduledAt) {
        CommunicationCampaign campaign = findOrThrow(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ConflictException("Only a draft campaign can be scheduled");
        }
        if (scheduledAt == null || !scheduledAt.isAfter(Instant.now())) {
            throw new InvalidRequestException("Scheduled time must be in the future");
        }
        campaign.setStatus(CampaignStatus.SCHEDULED);
        campaign.setScheduledAt(scheduledAt);
        campaignRepository.save(campaign);
    }

    // Called by CampaignScheduler only after it has already atomically claimed this campaign
    // (SCHEDULED -> PROCESSING via CommunicationCampaignRepository.claimForProcessing) — never
    // called directly from a controller.
    @Transactional
    public void resolveAndQueueClaimed(UUID campaignId) {
        CommunicationCampaign campaign = findOrThrow(campaignId);
        resolveAndQueue(campaign);
        triggerDeliveryAfterCommit(campaignId);
    }

    @Transactional
    public void retryFailed(UUID campaignId) {
        CommunicationCampaign campaign = findOrThrow(campaignId);
        if (campaign.getStatus() != CampaignStatus.PARTIALLY_SENT && campaign.getStatus() != CampaignStatus.FAILED) {
            throw new ConflictException("Only a partially-sent or failed campaign can be retried");
        }
        int requeued = recipientRepository.requeueFailed(campaignId);
        if (requeued == 0) {
            throw new ConflictException("No failed deliveries to retry");
        }
        campaign.setStatus(CampaignStatus.PROCESSING);
        campaignRepository.save(campaign);
        triggerDeliveryAfterCommit(campaignId);
    }

    // The delivery worker runs @Async on its own thread with its own DB connection in production.
    // Calling it directly from inside one of this class's own @Transactional methods (sendNow,
    // resolveAndQueueClaimed, retryFailed all update/insert rows the worker immediately needs to
    // read) races the worker against this transaction's commit: under READ_COMMITTED the worker's
    // connection can start its query before this one commits and see zero QUEUED rows, run its
    // batch loop over nothing, and immediately finalize — permanently stranding every recipient at
    // QUEUED with the campaign stuck at PROCESSING forever (live-verified while building this
    // feature). Deferring the trigger to afterCommit guarantees the worker's first read always
    // sees this transaction's writes.
    //
    // Only do this when delivery is genuinely async (app.communication.async.enabled=true, the
    // production default): when it's false, CommunicationTestSupport's SyncTaskExecutor runs the
    // worker synchronously on this SAME thread/transaction, which already sees this transaction's
    // own uncommitted writes without needing a commit at all — and every test in this suite is
    // itself wrapped in a single @Transactional that's rolled back at the end, so afterCommit would
    // simply never fire and the worker would never run.
    private void triggerDeliveryAfterCommit(UUID campaignId) {
        if (asyncDeliveryEnabled && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliveryWorker.processCampaignAsync(campaignId);
                }
            });
        } else {
            deliveryWorker.processCampaignAsync(campaignId);
        }
    }

    private void resolveAndQueue(CommunicationCampaign campaign) {
        List<UUID> userIds = audienceService.resolve(campaign.getFilterJson());
        List<User> users = userIds.isEmpty() ? List.of() : userRepository.findAllById(userIds);

        // Belt-and-suspenders against the DB unique constraint: checked up front (rather than
        // relying on catching a constraint-violation at flush time, which Hibernate would only
        // surface at the end of this transaction, by which point it's too late to skip just the
        // offending row) so resolveAndQueue stays safe to call more than once for the same
        // campaign without ever producing a duplicate row or aborting the whole batch.
        Set<String> existingKeys = new HashSet<>();
        for (CommunicationRecipient existing : recipientRepository.findAllByCampaignId(campaign.getId())) {
            existingKeys.add(recipientKey(existing.getUser().getId(), existing.getChannel()));
        }

        int eligibleUsers = 0;
        for (User user : users) {
            // Marketing campaigns must respect opt-out on every channel, not just email — an admin
            // cannot bypass this by only checking Email in the wizard.
            if (campaign.isMarketing() && user.isMarketingOptOut()) {
                continue;
            }
            eligibleUsers++;
            if (campaign.isSendEmail()) {
                queueRecipient(campaign, user, RecipientChannel.EMAIL, user.getEmail(), existingKeys);
            }
            if (campaign.isSendPush()) {
                queueRecipient(campaign, user, RecipientChannel.PUSH, null, existingKeys);
            }
            if (campaign.isSendInapp()) {
                queueRecipient(campaign, user, RecipientChannel.INAPP, null, existingKeys);
            }
        }
        campaign.setResolvedRecipientCount(eligibleUsers);
        campaignRepository.save(campaign);
    }

    private void queueRecipient(CommunicationCampaign campaign, User user, RecipientChannel channel, String email,
            Set<String> existingKeys) {
        String key = recipientKey(user.getId(), channel);
        if (!existingKeys.add(key)) {
            return;
        }
        recipientRepository.save(new CommunicationRecipient(campaign, user, channel, email));
    }

    private String recipientKey(UUID userId, RecipientChannel channel) {
        return userId + ":" + channel;
    }

    private CommunicationCampaign findOrThrow(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
    }
}
