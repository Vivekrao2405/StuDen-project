package com.studen.communication;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure aggregate reads over {@link CommunicationRecipient} — no separate analytics storage, no
 * estimation. A status this app never actually writes for a channel (e.g. DELIVERED for
 * push/in-app, which this app has no provider signal for) simply never appears with a non-zero
 * count, which is the honest answer rather than a fabricated one.
 */
@Service
public class CommunicationAnalyticsService {

    private final CommunicationRecipientRepository recipientRepository;

    public CommunicationAnalyticsService(CommunicationRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    @Transactional(readOnly = true)
    public CampaignAnalyticsResponse forCampaign(UUID campaignId) {
        List<CommunicationRecipient> all = recipientRepository.findAllByCampaignId(campaignId);
        return new CampaignAnalyticsResponse(
                tally(all, RecipientChannel.EMAIL),
                tally(all, RecipientChannel.PUSH),
                tally(all, RecipientChannel.INAPP));
    }

    private Map<RecipientStatus, Long> tally(List<CommunicationRecipient> all, RecipientChannel channel) {
        Map<RecipientStatus, Long> counts = new EnumMap<>(RecipientStatus.class);
        for (RecipientStatus status : RecipientStatus.values()) {
            counts.put(status, 0L);
        }
        for (CommunicationRecipient recipient : all) {
            if (recipient.getChannel() == channel) {
                counts.merge(recipient.getStatus(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
