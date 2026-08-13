package com.studen.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.booking.ServiceRequestResponse;
import com.studen.marketplace.ServiceResponse;
import com.studen.notification.NotificationType;
import com.studen.notification.RecordingNotifier;
import com.studen.user.UserRepository;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// See ServiceRequestControllerTest for why the auth rate limit needs raising (shared per-IP
// counter across every @SpringBootTest in this JVM run). app.messaging.max-messages-per-window is
// lowered so the 429 test doesn't need to send dozens of messages.
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-requests=100000",
        "app.messaging.max-messages-per-window=3",
        "app.messaging.rate-limit-window-minutes=60"
})
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingNotifier recordingNotifier;

    @Autowired
    private UserRepository userRepository;

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    private String registerWithPortfolio(String email) throws Exception {
        String token = registerAndGetToken(email);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "headline": "Test Student", "location": "Hyderabad", "available": true }
                                """))
                .andExpect(status().isCreated());
        return token;
    }

    // Overload used only by the concurrency test below, which runs outside the transactional
    // rollback every other test in this class relies on — a distinct full name (not "Test User")
    // avoids colliding with other tests' portfolio-slug expectations once this data is actually
    // committed to the shared dev database. See ServiceRequestControllerTest's identical overload
    // and its comment for why this matters (a real pollution bug was caught and fixed in 6.6).
    private String registerWithPortfolio(String fullName, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(body, AuthResponse.class).accessToken();
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "headline": "Test Student", "location": "Hyderabad", "available": true }
                                """))
                .andExpect(status().isCreated());
        return token;
    }

    private ServiceResponse createAndPublishService(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "category": "TECHNOLOGY",
                                  "description": "A full description of what this service offers.",
                                  "priceAmount": 1500,
                                  "deliveryDays": 3
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ServiceResponse created = objectMapper.readValue(body, ServiceResponse.class);

        String publishedBody = mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(publishedBody, ServiceResponse.class);
    }

    private ServiceRequestResponse createRequestAndReturn(String requesterToken, String serviceId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "description": "I need help with this project, please assist." }
                                """.formatted(serviceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ServiceRequestResponse.class);
    }

    private void acceptRequest(String providerToken, UUID requestId) throws Exception {
        mockMvc.perform(post("/api/v1/service-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());
    }

    private record AcceptedSetup(String providerToken, String requesterToken, UUID requestId) {
    }

    /** Full setup: provider + requester + published service + accepted request, ready for
     * messaging. Callers pass a unique-per-test label so emails/titles never collide. */
    private AcceptedSetup acceptedRequestSetup(String label) throws Exception {
        String providerToken = registerWithPortfolio("convo-" + label + "-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Service " + label);
        String requesterToken = registerWithPortfolio("convo-" + label + "-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());
        acceptRequest(providerToken, request.id());
        return new AcceptedSetup(providerToken, requesterToken, request.id());
    }

    private UUID getOrCreateConversationId(String token, UUID requestId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/service-requests/" + requestId + "/conversation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    // --- Conversation creation / acceptance gating -----------------------------------------

    @Test
    void getOrCreateConversation_byRequesterAfterAcceptance_succeeds() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("requester-open");

        mockMvc.perform(post("/api/v1/service-requests/" + setup.requestId() + "/conversation")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otherParticipantName").value("Test User"))
                .andExpect(jsonPath("$.serviceRequestId").value(setup.requestId().toString()));
    }

    @Test
    void getOrCreateConversation_byProviderAfterAcceptance_returnsSameConversationAsRequester() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("both-open");

        UUID requesterView = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        UUID providerView = getOrCreateConversationId(setup.providerToken(), setup.requestId());

        assertThat(requesterView).isEqualTo(providerView);
    }

    @Test
    void getOrCreateConversation_forPendingRequest_returns409() throws Exception {
        String providerToken = registerWithPortfolio("convo-pending-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Pending Service");
        String requesterToken = registerWithPortfolio("convo-pending-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + request.id() + "/conversation")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrCreateConversation_forRejectedRequest_returns409() throws Exception {
        String providerToken = registerWithPortfolio("convo-rejected-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Rejected Service");
        String requesterToken = registerWithPortfolio("convo-rejected-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());
        mockMvc.perform(post("/api/v1/service-requests/" + request.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + request.id() + "/conversation")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrCreateConversation_byUnrelatedUser_returns404() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("unrelated-create");
        String outsiderToken = registerWithPortfolio("convo-outsider-create@example.com");

        mockMvc.perform(post("/api/v1/service-requests/" + setup.requestId() + "/conversation")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrCreateConversation_calledTwice_doesNotCreateDuplicate() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("no-dup");

        UUID first = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        UUID second = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        assertThat(first).isEqualTo(second);
    }

    // Runs outside the transactional rollback (see ServiceRequestControllerTest's identical race
    // test for why: setup data must actually commit for two background threads on separate
    // connections to see it) and cleans up everything it commits in a finally block — the same
    // self-cleaning shape adopted after the pollution bug hit in Phase 6.6.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getOrCreateConversation_calledConcurrently_createsExactlyOneConversation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String providerEmail = "convo-race-provider-" + suffix + "@example.com";
        String requesterEmail = "convo-race-requester-" + suffix + "@example.com";
        String providerToken = registerWithPortfolio("Race Provider", providerEmail);
        ServiceResponse service = createAndPublishService(providerToken, "Race Conversation Service");
        String requesterToken = registerWithPortfolio("Race Requester", requesterEmail);
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());
        acceptRequest(providerToken, request.id());

        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            Callable<UUID> requesterOpen = () -> {
                ready.countDown();
                go.await();
                return getOrCreateConversationId(requesterToken, request.id());
            };
            Callable<UUID> providerOpen = () -> {
                ready.countDown();
                go.await();
                return getOrCreateConversationId(providerToken, request.id());
            };

            Future<UUID> requesterResult = pool.submit(requesterOpen);
            Future<UUID> providerResult = pool.submit(providerOpen);
            ready.await();
            go.countDown();

            UUID requesterConversationId = requesterResult.get();
            UUID providerConversationId = providerResult.get();
            pool.shutdown();

            assertThat(requesterConversationId).isEqualTo(providerConversationId);
        } finally {
            userRepository.findByEmail(providerEmail).ifPresent(u -> userRepository.deleteById(u.getId()));
            userRepository.findByEmail(requesterEmail).ifPresent(u -> userRepository.deleteById(u.getId()));
        }
    }

    // --- Messages: send / list / IDOR --------------------------------------------------------

    @Test
    void sendMessage_byParticipant_succeedsAndNotifiesOtherParticipantOnly() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("send-notify");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        recordingNotifier.clear();

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Hi, I wanted to discuss the project requirements." }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hi, I wanted to discuss the project requirements."))
                .andExpect(jsonPath("$.mine").value(true));

        assertThat(recordingNotifier.all())
                .anyMatch(n -> n.type() == NotificationType.NEW_MESSAGE && n.resourceId().equals(conversationId)
                        && n.message().contains("New message from"));
    }

    @Test
    void listMessages_returnsChronologicalOrder() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("chronological");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "First message" }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Second message" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("First message"))
                .andExpect(jsonPath("$[1].content").value("Second message"));
    }

    @Test
    void getConversation_byOutsider_returns404() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("outsider-get-convo");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        String outsiderToken = registerWithPortfolio("convo-outsider-get@example.com");

        mockMvc.perform(get("/api/v1/messages/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMessages_byOutsider_returns404() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("outsider-list-messages");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        String outsiderToken = registerWithPortfolio("convo-outsider-list@example.com");

        mockMvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendMessage_byOutsider_returns404() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("outsider-send");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        String outsiderToken = registerWithPortfolio("convo-outsider-send@example.com");

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "I shouldn't be able to send this." }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_byOutsider_returns404() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("outsider-read");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        String outsiderToken = registerWithPortfolio("convo-outsider-read@example.com");

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/read")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    // --- Validation / XSS / rate limiting ----------------------------------------------------

    @Test
    void sendMessage_blank_returns400() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("blank-message");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "   " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void sendMessage_tooLong_returns400() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("long-message");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        String tooLong = "x".repeat(2001);

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMessageRequest(tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void sendMessage_xssPayload_isStoredAndReturnedVerbatim() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("xss");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());
        String payload = "<script>alert(1)</script>";

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMessageRequest(payload))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(payload));

        mockMvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value(payload));
    }

    @Test
    void sendMessage_exceedingRateLimitWindow_returns429() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("rate-limit");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                            .header("Authorization", "Bearer " + setup.requesterToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "content": "Message number %d" }
                                    """.formatted(i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "One too many" }
                                """))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    // --- Unread / read state -----------------------------------------------------------------

    @Test
    void unreadCount_reflectsOnlyIncomingUnreadMessages_andMarkReadClearsItForThatParticipantOnly() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("unread");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Are you available?" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/messages/conversations")
                        .header("Authorization", "Bearer " + setup.providerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(1));

        mockMvc.perform(get("/api/v1/messages/conversations")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(0));

        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/read")
                        .header("Authorization", "Bearer " + setup.providerToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/messages/conversations")
                        .header("Authorization", "Bearer " + setup.providerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
    }

    @Test
    void sendMessage_neverNotifiesSenderThemselves() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("no-self-notify");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        String requesterBody = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andReturn().getResponse().getContentAsString();
        UUID requesterId = UUID.fromString(objectMapper.readTree(requesterBody).get("id").asText());

        recordingNotifier.clear();
        mockMvc.perform(post("/api/v1/messages/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "Hello there" }
                                """))
                .andExpect(status().isCreated());

        assertThat(recordingNotifier.notificationsFor(requesterId)).isEmpty();
    }

    // --- Privacy -------------------------------------------------------------------------------

    @Test
    void getConversation_neverExposesEmailOrInternalIds() throws Exception {
        AcceptedSetup setup = acceptedRequestSetup("privacy");
        UUID conversationId = getOrCreateConversationId(setup.requesterToken(), setup.requestId());

        String body = mockMvc.perform(get("/api/v1/messages/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("convo-privacy-provider@example.com");
        assertThat(body).doesNotContainIgnoringCase("\"email\"");
        assertThat(body).doesNotContainIgnoringCase("password");
        assertThat(body).doesNotContainIgnoringCase("requesterId");
        assertThat(body).doesNotContainIgnoringCase("providerId");
    }
}
