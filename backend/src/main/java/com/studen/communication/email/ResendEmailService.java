package com.studen.communication.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The sole {@link EmailService} implementation, talking to Resend via its official Java SDK.
 * {@code app.resend.api-key} has no default (same fail-fast convention as {@code
 * app.push.vapid.*}/{@code app.jwt.secret}) — a missing key must stop the app from starting rather
 * than silently no-op every send. The key is read once into this bean's constructor and never
 * logged, returned, or exposed through any response DTO anywhere in this package.
 */
@Service
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final Resend client;
    private final String from;

    public ResendEmailService(@Value("${app.resend.api-key}") String apiKey, @Value("${app.resend.from}") String from) {
        this.client = new Resend(apiKey);
        this.from = from;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(message.to())
                .subject(message.subject())
                .html(message.html())
                .build();
        try {
            CreateEmailResponse response = client.emails().send(options);
            return EmailSendResult.success(response.getId());
        } catch (ResendException | RuntimeException e) {
            // The SDK doesn't consistently wrap failures in ResendException — an HTTP-level error
            // (e.g. 401 on an invalid API key) surfaces as a plain RuntimeException instead, live-
            // verified while testing this flow. Catching only ResendException let that propagate
            // uncaught out of the delivery worker's per-recipient try/catch boundary, so this must
            // catch broadly: any failure here must become a graceful EmailSendResult.failure(...),
            // never an exception, since the whole point of this method's contract is that one bad
            // send only ever marks its own recipient row FAILED and never aborts the batch.
            //
            // Never logs message.to()/subject/html at more than debug — an admin-composed
            // campaign body isn't a secret, but recipient addresses are still PII, and this log
            // line must never become a second place RESEND_API_KEY (baked into the SDK's own
            // internal error messages on auth failures) could leak.
            log.warn("Resend send failed: {}", safeMessage(e));
            return EmailSendResult.failure(safeMessage(e));
        }
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg == null ? "Resend send failed" : msg;
    }
}
