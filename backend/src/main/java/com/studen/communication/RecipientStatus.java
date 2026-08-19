package com.studen.communication;

/**
 * QUEUED is the only state {@link CampaignSendService} ever inserts. Everything else is written
 * only from a real provider outcome — a successful API call moves a row to SENT, never DELIVERED
 * (that's webhook-only, see {@code ResendWebhookController}); a thrown/error response moves it to
 * FAILED with {@code errorMessage} set. SKIPPED covers "no email on file"/"opted out of
 * marketing" — a deliberate non-send, not a failure.
 */
public enum RecipientStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED,
    BOUNCED,
    COMPLAINED,
    SKIPPED
}
