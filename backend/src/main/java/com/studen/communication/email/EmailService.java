package com.studen.communication.email;

/**
 * The only interface any campaign-sending code depends on — {@link ResendEmailService} is the
 * sole current implementation, but nothing outside this package knows that. Swapping providers
 * later (spec: "keep the email provider behind EmailService so it can be replaced later") means
 * adding one new implementation class and changing zero call sites.
 */
public interface EmailService {

    EmailSendResult send(EmailMessage message);
}
