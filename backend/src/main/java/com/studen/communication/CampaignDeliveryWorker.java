package com.studen.communication;

import com.studen.communication.email.EmailMessage;
import com.studen.communication.email.EmailSendResult;
import com.studen.communication.email.EmailService;
import com.studen.notification.NotificationService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processes one campaign's QUEUED {@link CommunicationRecipient} rows in small batches, on its own
 * {@code communicationTaskExecutor} thread so neither {@code CampaignSendService}'s caller (an
 * admin HTTP request) nor {@code CampaignScheduler}'s poll thread ever blocks on a send. Every
 * recipient is attempted independently — one bad email/push/in-app send only ever marks that one
 * row FAILED, never aborts the batch (spec: "if one email fails, do not fail the entire
 * campaign"). DB access lives in the injected {@link CampaignDeliveryTransactions} bean (never
 * called via {@code this} — see that class's javadoc) so the actual provider network call here
 * never holds a database transaction open.
 */
@Service
public class CampaignDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(CampaignDeliveryWorker.class);
    private static final int BATCH_SIZE = 20;
    private static final long BATCH_PAUSE_MS = 250;

    private final CommunicationRecipientRepository recipientRepository;
    private final CampaignDeliveryTransactions transactions;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public CampaignDeliveryWorker(CommunicationRecipientRepository recipientRepository,
            CampaignDeliveryTransactions transactions, EmailService emailService,
            NotificationService notificationService) {
        this.recipientRepository = recipientRepository;
        this.transactions = transactions;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @Async("communicationTaskExecutor")
    public void processCampaignAsync(UUID campaignId) {
        List<UUID> queuedIds = recipientRepository.findAllByCampaignIdAndStatus(campaignId, RecipientStatus.QUEUED)
                .stream().map(CommunicationRecipient::getId).toList();

        for (int i = 0; i < queuedIds.size(); i += BATCH_SIZE) {
            List<UUID> batch = queuedIds.subList(i, Math.min(i + BATCH_SIZE, queuedIds.size()));
            for (UUID recipientId : batch) {
                try {
                    processOne(recipientId);
                } catch (Exception e) {
                    // Whatever threw here happened before processOne reached its own
                    // transactions.recordResult call (a provider client throwing something
                    // EmailService's own contract didn't catch, a DB constraint violation, etc.) —
                    // without this, that recipient row is left QUEUED forever: invisible to
                    // retry-failed (which only ever touches FAILED rows) and permanently blocking
                    // finalizeCampaignStatus's stillQueued > 0 check, so the whole campaign would
                    // never leave PROCESSING. Marking it FAILED here is what actually makes good on
                    // this class's documented guarantee that one bad send only ever marks its own
                    // row FAILED, never stalls the batch.
                    log.error("Unexpected failure processing communication recipient {}", recipientId, e);
                    try {
                        transactions.recordResult(recipientId, RecipientStatus.FAILED, null, safeMessage(e));
                    } catch (Exception recordFailure) {
                        log.error("Failed to record FAILED status for communication recipient {}", recipientId,
                                recordFailure);
                    }
                }
            }
            if (i + BATCH_SIZE < queuedIds.size()) {
                sleepBetweenBatches();
            }
        }

        transactions.finalizeCampaignStatus(campaignId);
    }

    private void processOne(UUID recipientId) {
        CampaignDeliveryTransactions.RecipientContext ctx = transactions.loadContext(recipientId);
        if (ctx == null) {
            return;
        }
        switch (ctx.channel()) {
            case EMAIL -> {
                EmailSendResult result = emailService.send(new EmailMessage(ctx.email(), ctx.subject(), ctx.body()));
                transactions.recordResult(recipientId, result.success() ? RecipientStatus.SENT : RecipientStatus.FAILED,
                        result.providerMessageId(), result.errorMessage());
            }
            case PUSH -> {
                notificationService.notifyForCampaign(ctx.userId(), ctx.body(), ctx.ctaUrl(), false, true);
                // PushDispatcher fires-and-forgets on its own executor and never reports success/
                // failure back synchronously (by design — see PushDispatcher javadoc), so the only
                // honest status here is SENT ("handed off"), never DELIVERED.
                transactions.recordResult(recipientId, RecipientStatus.SENT, null, null);
            }
            case INAPP -> {
                notificationService.notifyForCampaign(ctx.userId(), ctx.body(), ctx.ctaUrl(), true, false);
                transactions.recordResult(recipientId, RecipientStatus.SENT, null, null);
            }
        }
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    private void sleepBetweenBatches() {
        try {
            Thread.sleep(BATCH_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
