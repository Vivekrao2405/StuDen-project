package com.studen.communication;

import com.studen.communication.email.ResendWebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Public endpoint (see {@code SecurityConfig.publicEndpoints} — it's added there, not gated by
 * JWT, since Resend's servers can't authenticate as a StuDen user) but every request is verified
 * independently via {@link ResendWebhookVerifier} before anything in the body is trusted. The body
 * is bound as raw {@code byte[]} rather than a parsed DTO/String — Jackson's message converter
 * would otherwise try to deserialize an {@code application/json} body directly into a {@code
 * String} and fail; binding bytes sidesteps that and, more importantly, guarantees the exact bytes
 * used for signature verification are the exact bytes Resend sent, with no
 * re-serialization/whitespace drift.
 */
@RestController
@RequestMapping("/api/v1/webhooks/resend")
public class ResendWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookController.class);

    private final ResendWebhookVerifier verifier;
    private final CommunicationRecipientRepository recipientRepository;
    private final ObjectMapper objectMapper;

    public ResendWebhookController(ResendWebhookVerifier verifier, CommunicationRecipientRepository recipientRepository,
            ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.recipientRepository = recipientRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature,
            @RequestBody byte[] rawBody) {
        String body = new String(rawBody, StandardCharsets.UTF_8);

        if (!verifier.verify(svixId, svixTimestamp, svixSignature, body)) {
            log.warn("Rejected Resend webhook with invalid/missing signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        String type = root.path("type").asString(null);
        String emailId = root.path("data").path("email_id").asString(null);
        if (type == null || emailId == null) {
            return ResponseEntity.ok().build();
        }

        applyEvent(type, emailId);
        return ResponseEntity.ok().build();
    }

    // Each repository call below is independently transactional via Spring Data's repository
    // proxy (no explicit @Transactional needed here) — the recipient entity is only touched via
    // scalar setters in between, never a lazy relation, so there's no cross-call session needed.
    void applyEvent(String type, String providerMessageId) {
        Optional<CommunicationRecipient> found = recipientRepository.findByProviderMessageId(providerMessageId);
        if (found.isEmpty()) {
            // Not a message this app sent (or the row is gone) — not an error; Resend still
            // expects a 200 so it doesn't keep retrying a webhook we can never act on.
            return;
        }
        CommunicationRecipient recipient = found.get();
        Instant now = Instant.now();
        switch (type) {
            case "email.delivered" -> {
                recipient.setStatus(RecipientStatus.DELIVERED);
                recipient.setDeliveredAt(now);
            }
            case "email.bounced" -> recipient.setStatus(RecipientStatus.BOUNCED);
            case "email.complained" -> recipient.setStatus(RecipientStatus.COMPLAINED);
            case "email.opened" -> recipient.setOpenedAt(now);
            case "email.clicked" -> recipient.setClickedAt(now);
            default -> {
                // email.sent/email.scheduled/email.delivery_delayed/email.failed and any other
                // event type StuDen doesn't currently track a distinct status for — left alone
                // rather than guessed at.
                return;
            }
        }
        recipientRepository.save(recipient);
    }
}
