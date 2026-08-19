package com.studen.communication.email;

/**
 * {@code to} is always resolved server-side from {@code User.email} by the caller
 * (CampaignDeliveryWorker) — never from a client-submitted address. This keeps {@link
 * EmailService} provider-agnostic: nothing about Resend leaks into this shape.
 */
public record EmailMessage(String to, String subject, String html) {
}
