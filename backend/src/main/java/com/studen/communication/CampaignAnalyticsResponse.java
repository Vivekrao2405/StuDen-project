package com.studen.communication;

import java.util.Map;

/**
 * Only ever populated from real {@link CommunicationRecipient} rows — a metric with no data (e.g.
 * "opened" before any webhook has been configured/received) reads as 0, which is honest: it means
 * "0 confirmed", not "confirmed 0". Never fabricated or estimated.
 */
public record CampaignAnalyticsResponse(
        Map<RecipientStatus, Long> email,
        Map<RecipientStatus, Long> push,
        Map<RecipientStatus, Long> inapp) {
}
