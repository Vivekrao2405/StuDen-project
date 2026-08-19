package com.studen.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies {@code ResendWebhookVerifier}'s Svix HMAC algorithm end-to-end and that
 * {@code ResendWebhookController} actually applies delivery-status updates only for a validly
 * signed request. Signs test payloads with the exact same algorithm as production
 * (HMAC-SHA256 over "{id}.{timestamp}.{body}", base64-decoded whsec_ secret) so a webhook this
 * app itself signed the same way Resend would is provably accepted, and a tampered/garbage
 * signature is provably rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CommunicationTestSupport.class)
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000",
        "app.communication.async.enabled=false"})
class ResendWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunicationCampaignRepository campaignRepository;

    @Autowired
    private CommunicationRecipientRepository recipientRepository;

    @Value("${app.resend.webhook-secret}")
    private String webhookSecret;

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    private String sign(String svixId, String svixTimestamp, String body) throws Exception {
        String secretB64 = webhookSecret.startsWith("whsec_") ? webhookSecret.substring("whsec_".length()) : webhookSecret;
        byte[] key = Base64.getDecoder().decode(secretB64);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] digest = mac.doFinal((svixId + "." + svixTimestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(digest);
    }

    @Test
    void validSignature_deliveredEvent_updatesRecipientStatus() throws Exception {
        registerAndGetToken("wh-recipient@example.com");
        User user = userRepository.findByEmail("wh-recipient@example.com").orElseThrow();
        User admin = user; // any persisted user works as the campaign's createdBy for this test

        CommunicationCampaign campaign = new CommunicationCampaign("Webhook test", CommunicationCategory.CUSTOM, "{}",
                admin);
        campaign = campaignRepository.save(campaign);
        CommunicationRecipient recipient = new CommunicationRecipient(campaign, user, RecipientChannel.EMAIL,
                user.getEmail());
        recipient.setStatus(RecipientStatus.SENT);
        recipient.setProviderMessageId("resend-msg-123");
        recipientRepository.save(recipient);

        String body = """
                {"type":"email.delivered","created_at":"2026-01-01T00:00:00.000Z","data":{"email_id":"resend-msg-123"}}""";
        String svixId = "msg_test123";
        String svixTimestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(svixId, svixTimestamp, body);

        mockMvc.perform(post("/api/v1/webhooks/resend")
                        .header("svix-id", svixId)
                        .header("svix-timestamp", svixTimestamp)
                        .header("svix-signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        CommunicationRecipient updated = recipientRepository.findByProviderMessageId("resend-msg-123").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(RecipientStatus.DELIVERED);
        assertThat(updated.getDeliveredAt()).isNotNull();
    }

    @Test
    void invalidSignature_isRejectedWith401_andNeverAppliesTheEvent() throws Exception {
        registerAndGetToken("wh-bad-sig@example.com");
        User user = userRepository.findByEmail("wh-bad-sig@example.com").orElseThrow();

        CommunicationCampaign campaign = campaignRepository.save(
                new CommunicationCampaign("Bad sig test", CommunicationCategory.CUSTOM, "{}", user));
        CommunicationRecipient recipient = new CommunicationRecipient(campaign, user, RecipientChannel.EMAIL,
                user.getEmail());
        recipient.setStatus(RecipientStatus.SENT);
        recipient.setProviderMessageId("resend-msg-bad-sig");
        recipientRepository.save(recipient);

        String body = """
                {"type":"email.delivered","data":{"email_id":"resend-msg-bad-sig"}}""";

        mockMvc.perform(post("/api/v1/webhooks/resend")
                        .header("svix-id", "msg_bad")
                        .header("svix-timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("svix-signature", "v1,not-a-real-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        CommunicationRecipient stillSent = recipientRepository.findByProviderMessageId("resend-msg-bad-sig")
                .orElseThrow();
        assertThat(stillSent.getStatus()).isEqualTo(RecipientStatus.SENT);
    }

    @Test
    void missingSignatureHeaders_rejectedByControllerItself_notByTheJwtFilter() throws Exception {
        // Both a missing-signature rejection (ResendWebhookController) and a missing-JWT rejection
        // (JwtAuthenticationEntryPoint) return 401 — asserting the status alone wouldn't prove this
        // endpoint is actually public. JwtAuthenticationEntryPoint's body always contains
        // "Authentication is required..."; this controller's own 401 has no body at all, so its
        // absence here proves the request reached the controller (i.e. the path really is on
        // SecurityConfig's public matcher) rather than being stopped by the JWT filter first.
        String responseBody = mockMvc.perform(post("/api/v1/webhooks/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"whatever\"}}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain("Authentication is required");
    }
}
