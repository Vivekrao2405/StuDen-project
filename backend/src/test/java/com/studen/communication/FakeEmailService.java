package com.studen.communication;

import com.studen.communication.email.EmailMessage;
import com.studen.communication.email.EmailSendResult;
import com.studen.communication.email.EmailService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double standing in for {@link com.studen.communication.email.ResendEmailService} — real
 * campaign-send tests must never depend on network access to the actual Resend API. Registered as
 * {@code @Primary} by {@link CommunicationTestSupport}. {@code failFor} lets a test simulate a
 * per-recipient provider failure to exercise the "one bad email never fails the whole campaign"
 * behavior.
 */
public class FakeEmailService implements EmailService {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile Set<String> failFor = Set.of();

    @Override
    public EmailSendResult send(EmailMessage message) {
        sent.add(message);
        if (failFor.contains(message.to())) {
            return EmailSendResult.failure("Simulated provider failure for test");
        }
        return EmailSendResult.success("test-message-" + sent.size());
    }

    public void failFor(String... emails) {
        this.failFor = Set.of(emails);
    }

    public void reset() {
        sent.clear();
        failFor = Set.of();
    }

    public List<EmailMessage> sent() {
        return new ArrayList<>(sent);
    }
}
