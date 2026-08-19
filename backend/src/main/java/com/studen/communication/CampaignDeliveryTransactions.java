package com.studen.communication;

import com.studen.user.User;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The DB-transactional half of {@link CampaignDeliveryWorker}, split into its own bean for the
 * same reason {@code PushDispatcher} is split from {@code NotificationService}: {@code
 * processCampaignAsync} calls these methods on {@code this} would bypass Spring's proxy and
 * silently run without a transaction at all (open-in-view is disabled app-wide), which would
 * throw {@code LazyInitializationException} the moment {@link #loadContext} touches {@code
 * recipient.getUser()}/{@code recipient.getCampaign()}. Going through an injected bean instead
 * ensures {@code @Transactional} actually applies.
 */
@Service
class CampaignDeliveryTransactions {

    private final CommunicationRecipientRepository recipientRepository;
    private final CommunicationCampaignRepository campaignRepository;
    private final MessageTemplateRenderer renderer;

    CampaignDeliveryTransactions(CommunicationRecipientRepository recipientRepository,
            CommunicationCampaignRepository campaignRepository, MessageTemplateRenderer renderer) {
        this.recipientRepository = recipientRepository;
        this.campaignRepository = campaignRepository;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    RecipientContext loadContext(UUID recipientId) {
        CommunicationRecipient recipient = recipientRepository.findById(recipientId).orElse(null);
        if (recipient == null || recipient.getStatus() != RecipientStatus.QUEUED) {
            return null;
        }
        CommunicationCampaign campaign = recipient.getCampaign();
        User user = recipient.getUser();

        Map<String, String> values = new LinkedHashMap<>();
        String fullName = user.getFullName() == null ? "" : user.getFullName();
        int space = fullName.indexOf(' ');
        values.put("firstName", space > 0 ? fullName.substring(0, space) : (fullName.isBlank() ? "Student" : fullName));
        values.put("lastName", space > 0 ? fullName.substring(space + 1) : "");

        String subject = renderer.render(campaign.getEmailSubject(), values);
        String body = switch (recipient.getChannel()) {
            case EMAIL -> renderer.render(campaign.getEmailBodyHtml(), values);
            case PUSH -> renderer.render(campaign.getPushBody(), values);
            case INAPP -> renderer.render(campaign.getInappBody(), values);
        };
        return new RecipientContext(recipient.getChannel(), user.getId(), recipient.getRecipientEmail(), subject,
                body, campaign.getCtaUrl());
    }

    @Transactional
    void recordResult(UUID recipientId, RecipientStatus status, String providerMessageId, String errorMessage) {
        CommunicationRecipient recipient = recipientRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        recipient.setStatus(status);
        recipient.setProviderMessageId(providerMessageId);
        recipient.setErrorMessage(errorMessage);
        recipientRepository.save(recipient);
    }

    @Transactional
    void finalizeCampaignStatus(UUID campaignId) {
        CommunicationCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null || campaign.getStatus() != CampaignStatus.PROCESSING) {
            return;
        }
        List<CommunicationRecipient> all = recipientRepository.findAllByCampaignId(campaignId);
        long attempted = all.stream().filter(r -> r.getStatus() != RecipientStatus.SKIPPED).count();
        long failed = all.stream().filter(r -> r.getStatus() == RecipientStatus.FAILED).count();
        long stillQueued = all.stream().filter(r -> r.getStatus() == RecipientStatus.QUEUED).count();

        if (stillQueued > 0) {
            return;
        }
        if (attempted == 0 || failed == 0) {
            campaign.setStatus(CampaignStatus.SENT);
        } else if (failed == attempted) {
            campaign.setStatus(CampaignStatus.FAILED);
        } else {
            campaign.setStatus(CampaignStatus.PARTIALLY_SENT);
        }
        campaign.setSentAt(Instant.now());
        campaignRepository.save(campaign);
    }

    record RecipientContext(RecipientChannel channel, UUID userId, String email, String subject, String body,
            String ctaUrl) {
    }
}
