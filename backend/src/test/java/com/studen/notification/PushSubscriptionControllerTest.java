package com.studen.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PushSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Push Tester", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    private String subscribePayload(String endpoint) {
        return """
                { "endpoint": "%s", "p256dh": "fake-p256dh-key", "auth": "fake-auth-secret" }
                """.formatted(endpoint);
    }

    @Test
    void getVapidPublicKey_returnsConfiguredKey() throws Exception {
        String token = registerAndGetToken("vapid-key@example.com");

        mockMvc.perform(get("/api/v1/push/vapid-public-key").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isNotEmpty());
    }

    @Test
    void register_withValidPayload_returns201() throws Exception {
        String token = registerAndGetToken("subscribe-valid@example.com");

        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload("https://push.example.com/endpoint-a")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void register_withBlankEndpoint_returns400() throws Exception {
        String token = registerAndGetToken("subscribe-blank@example.com");

        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void register_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload("https://push.example.com/no-auth")))
                .andExpect(status().isUnauthorized());
    }

    // The same browser endpoint re-subscribing under a different user (device handoff, shared
    // kiosk, storage-cleared re-subscribe) must reassign the existing row rather than create a
    // duplicate pointing at the same push channel under two owners.
    @Test
    void register_sameEndpointUnderDifferentUser_reassignsOwnership() throws Exception {
        String tokenA = registerAndGetToken("device-handoff-a@example.com");
        String tokenB = registerAndGetToken("device-handoff-b@example.com");
        String endpoint = "https://push.example.com/shared-device";

        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload(endpoint)))
                .andExpect(status().isCreated());

        long countAfterFirst = pushSubscriptionRepository.findByEndpoint(endpoint).stream().count();
        assertThat(countAfterFirst).isEqualTo(1);

        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload(endpoint)))
                .andExpect(status().isCreated());

        Optional<PushSubscription> reassigned = pushSubscriptionRepository.findByEndpoint(endpoint);
        assertThat(reassigned).isPresent();
        assertThat(reassigned.get().getUser().getEmail()).isEqualTo("device-handoff-b@example.com");
        // Still exactly one row for this endpoint — reassigned, not duplicated.
        assertThat(pushSubscriptionRepository.findAll().stream().filter(s -> s.getEndpoint().equals(endpoint)).count())
                .isEqualTo(1);
    }

    @Test
    void unregister_byOwner_removesSubscription() throws Exception {
        String token = registerAndGetToken("unsubscribe-owner@example.com");
        String endpoint = "https://push.example.com/owner-delete";
        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload(endpoint)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .param("endpoint", endpoint))
                .andExpect(status().isNoContent());

        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint)).isEmpty();
    }

    // A user must never be able to delete another user's subscription — the endpoint belongs to
    // User B, so User A's delete call must be a silent no-op, not an actual deletion.
    @Test
    void unregister_byNonOwner_doesNotDeleteAnotherUsersSubscription() throws Exception {
        String tokenA = registerAndGetToken("unsubscribe-attacker@example.com");
        String tokenB = registerAndGetToken("unsubscribe-victim@example.com");
        String endpoint = "https://push.example.com/victim-endpoint";

        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload(endpoint)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("endpoint", endpoint))
                .andExpect(status().isNoContent());

        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint)).isPresent();
    }
}
