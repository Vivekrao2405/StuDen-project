package com.studen.communication.email;

/**
 * {@code success=true} only ever means the provider accepted the send request — it is
 * deliberately NOT "delivered". Delivery/bounce/complaint/open/click are provider-async events
 * that only {@code ResendWebhookController} is allowed to record.
 */
public record EmailSendResult(boolean success, String providerMessageId, String errorMessage) {

    public static EmailSendResult success(String providerMessageId) {
        return new EmailSendResult(true, providerMessageId, null);
    }

    public static EmailSendResult failure(String errorMessage) {
        return new EmailSendResult(false, null, errorMessage);
    }
}
