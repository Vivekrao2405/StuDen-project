package com.studen.communication.email;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies a Resend webhook request per Resend's documented Svix-based signing scheme (Resend
 * docs: dashboard/webhooks/verify-webhooks-requests; algorithm: docs.svix.com/receiving/
 * verifying-payloads/how-manual). Signed content is {@code "{svix-id}.{svix-timestamp}.{rawBody}"}
 * HMAC-SHA256'd with the base64-decoded portion of the {@code whsec_}-prefixed secret, base64
 * re-encoded, and compared (constant-time) against each {@code v1,<sig>} entry in the
 * space-delimited {@code svix-signature} header. {@code app.resend.webhook-secret} has no default
 * — an unconfigured secret means every webhook request is rejected, never silently trusted.
 */
@Component
public class ResendWebhookVerifier {

    // Rejects a replayed-but-otherwise-valid signature sent long after the fact — mirrors Svix's
    // own recommended tolerance, not something Resend's request itself enforces for us.
    private static final long TOLERANCE_SECONDS = 5 * 60;

    private final byte[] secretKeyBytes;

    public ResendWebhookVerifier(@Value("${app.resend.webhook-secret}") String webhookSecret) {
        String encoded = webhookSecret.startsWith("whsec_") ? webhookSecret.substring("whsec_".length())
                : webhookSecret;
        this.secretKeyBytes = Base64.getDecoder().decode(encoded);
    }

    public boolean verify(String svixId, String svixTimestamp, String svixSignature, String rawBody) {
        if (svixId == null || svixTimestamp == null || svixSignature == null || rawBody == null) {
            return false;
        }
        if (!withinTolerance(svixTimestamp)) {
            return false;
        }

        String expected = sign(svixId, svixTimestamp, rawBody);
        for (String part : svixSignature.trim().split("\\s+")) {
            int comma = part.indexOf(',');
            if (comma < 0) {
                continue;
            }
            String candidateSignature = part.substring(comma + 1);
            if (constantTimeEquals(candidateSignature, expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean withinTolerance(String svixTimestamp) {
        try {
            long ts = Long.parseLong(svixTimestamp);
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - ts) <= TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String svixId, String svixTimestamp, String rawBody) {
        try {
            String signedContent = svixId + "." + svixTimestamp + "." + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }
}
