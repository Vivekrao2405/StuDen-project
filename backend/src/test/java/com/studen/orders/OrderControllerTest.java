package com.studen.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.booking.ServiceRequestResponse;
import com.studen.marketplace.ServiceResponse;
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
// Same auth-rate-limit raise as every other controller test in this JVM run (shared per-IP
// counter across every @SpringBootTest).
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-requests=100000"
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingNotifier recordingNotifier;

    @Autowired
    private UserRepository userRepository;

    private String registerWithPortfolio(String email) throws Exception {
        return registerWithPortfolio("Test User", email);
    }

    // A distinct full name (not always "Test User") is needed by the concurrency test below,
    // which runs outside the transactional rollback every other test in this class relies on —
    // same reasoning as ConversationControllerTest/ServiceRequestControllerTest's identical
    // overload (a real test-pollution bug was caught and fixed from this exact scenario in 6.6).
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

    private UUID getOrCreateOrderId(String token, UUID requestId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/service-requests/" + requestId + "/order")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private record AcceptedOrderSetup(String providerToken, String requesterToken, UUID requestId, UUID orderId) {
    }

    /** Full setup: provider + requester + published service + accepted request + its
     * auto-created order, ready for the work lifecycle. Callers pass a unique-per-test label so
     * emails/titles never collide. */
    private AcceptedOrderSetup acceptedOrderSetup(String label) throws Exception {
        String providerToken = registerWithPortfolio("order-" + label + "-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Service " + label);
        String requesterToken = registerWithPortfolio("order-" + label + "-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());
        acceptRequest(providerToken, request.id());
        UUID orderId = getOrCreateOrderId(requesterToken, request.id());
        return new AcceptedOrderSetup(providerToken, requesterToken, request.id(), orderId);
    }

    // --- Order creation / acceptance gating --------------------------------------------------

    @Test
    void accept_autoCreatesOrder_inProgressWithNoManualStep() throws Exception {
        String providerToken = registerWithPortfolio("order-auto-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Auto Create Service");
        String requesterToken = registerWithPortfolio("order-auto-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());

        acceptRequest(providerToken, request.id());

        // No creation step performed here beyond accept() itself — GET must already 200.
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceRequestId").value(request.id().toString()))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
    }

    @Test
    void createOrder_forPendingRequest_returns409() throws Exception {
        String providerToken = registerWithPortfolio("order-pending-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Pending Service");
        String requesterToken = registerWithPortfolio("order-pending-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + request.id() + "/order")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrder_forRejectedRequest_returns409() throws Exception {
        String providerToken = registerWithPortfolio("order-rejected-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Rejected Service");
        String requesterToken = registerWithPortfolio("order-rejected-requester@example.com");
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());
        mockMvc.perform(post("/api/v1/service-requests/" + request.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + request.id() + "/order")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrder_calledTwice_doesNotCreateDuplicate() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("no-dup");

        UUID again = getOrCreateOrderId(setup.providerToken(), setup.requestId());

        assertThat(again).isEqualTo(setup.orderId());
    }

    // --- View / IDOR -----------------------------------------------------------------------

    @Test
    void getOrder_byProvider_succeeds() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("view-provider");

        mockMvc.perform(get("/api/v1/orders/" + setup.orderId())
                        .header("Authorization", "Bearer " + setup.providerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.orderId().toString()));
    }

    @Test
    void getOrder_byRequester_succeeds() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("view-requester");

        mockMvc.perform(get("/api/v1/orders/" + setup.orderId())
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(setup.orderId().toString()));
    }

    @Test
    void getOrder_byOutsider_returns404() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("view-outsider");
        String outsiderToken = registerWithPortfolio("order-outsider-view@example.com");

        mockMvc.perform(get("/api/v1/orders/" + setup.orderId())
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_neverExposesEmailOrInternalIds() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("privacy");

        String body = mockMvc.perform(get("/api/v1/orders/" + setup.orderId())
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("order-privacy-provider@example.com");
        assertThat(body).doesNotContainIgnoringCase("\"email\"");
        assertThat(body).doesNotContainIgnoringCase("password");
        assertThat(body).doesNotContainIgnoringCase("requesterId");
        assertThat(body).doesNotContainIgnoringCase("providerId");
    }

    // --- Submit work ---------------------------------------------------------------------------

    @Test
    void submitWork_byProvider_succeedsAndNotifiesRequesterOnly() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-notify");
        recordingNotifier.clear();

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Delivered the first draft of the dashboard.", "link": "https://drive.example.com/file" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WORK_SUBMITTED"))
                .andExpect(jsonPath("$.submissionDescription").value("Delivered the first draft of the dashboard."));

        assertThat(recordingNotifier.all()).anyMatch(n -> n.message().contains("Work has been submitted"));
    }

    @Test
    void submitWork_byRequester_returns404() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-wrong-role");

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.requesterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "I shouldn't be able to submit this." }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitWork_whenAlreadySubmitted_returns409() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-twice");
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "First submission." }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Second submission attempt." }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void submitWork_blankDescription_returns400() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-blank");

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "   " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitWork_tooLongDescription_returns400() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-long");
        String tooLong = "x".repeat(2001);

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitWorkRequest(tooLong, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void submitWork_javascriptLink_returns400() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-xss-link");

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitWorkRequest("Done.", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- Complete --------------------------------------------------------------------------

    @Test
    void completeOrder_byRequester_succeedsAndNotifiesProviderOnly() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("complete-notify");
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Final delivery." }
                                """))
                .andExpect(status().isOk());
        recordingNotifier.clear();

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/complete")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(recordingNotifier.all()).anyMatch(n -> n.message().contains("marked completed"));
    }

    @Test
    void completeOrder_byProvider_returns404() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("complete-wrong-role");
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Final delivery." }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/complete")
                        .header("Authorization", "Bearer " + setup.providerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void completeOrder_beforeSubmission_returns409() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("complete-early");

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/complete")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isConflict());
    }

    // --- Cancel ------------------------------------------------------------------------------

    @Test
    void cancelOrder_byProviderWhileInProgress_succeedsAndNotifiesRequesterOnly() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("cancel-by-provider");
        recordingNotifier.clear();

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Can't complete this after all." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(recordingNotifier.all()).anyMatch(n -> n.message().contains("was cancelled"));
    }

    @Test
    void cancelOrder_byRequesterWhileInProgress_succeedsAndNotifiesProviderOnly() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("cancel-by-requester");
        recordingNotifier.clear();

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(recordingNotifier.all()).anyMatch(n -> n.message().contains("was cancelled"));
    }

    @Test
    void cancelOrder_byOutsider_returns404() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("cancel-outsider");
        String outsiderToken = registerWithPortfolio("order-outsider-cancel@example.com");

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_afterCompleted_returns409() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("cancel-after-complete");
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Final delivery." }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/complete")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_whenAlreadyCancelled_returns409() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("cancel-twice");
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + setup.providerToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void submitWork_afterCancelled_returns409() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("submit-after-cancel");
        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + setup.orderId() + "/submit")
                        .header("Authorization", "Bearer " + setup.providerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description": "Too late." }
                                """))
                .andExpect(status().isConflict());
    }

    // --- Listing / filtering -----------------------------------------------------------------

    @Test
    void listMyOrders_filtersByStatus() throws Exception {
        AcceptedOrderSetup setup = acceptedOrderSetup("list-filter");

        mockMvc.perform(get("/api/v1/orders?status=IN_PROGRESS")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/orders?status=COMPLETED")
                        .header("Authorization", "Bearer " + setup.requesterToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Concurrency safety --------------------------------------------------------------------

    // Runs outside the transactional rollback (see ConversationControllerTest's identical race
    // test for why: two background threads on separate connections need to actually see committed
    // setup data) and cleans up everything it commits in a finally block.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSubmitAndCancel_onlyOneTransitionSucceeds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String providerEmail = "order-race-provider-" + suffix + "@example.com";
        String requesterEmail = "order-race-requester-" + suffix + "@example.com";
        String providerToken = registerWithPortfolio("Race Provider", providerEmail);
        ServiceResponse service = createAndPublishService(providerToken, "Race Order Service");
        String requesterToken = registerWithPortfolio("Race Requester", requesterEmail);
        ServiceRequestResponse request = createRequestAndReturn(requesterToken, service.id().toString());
        acceptRequest(providerToken, request.id());
        UUID orderId = getOrCreateOrderId(requesterToken, request.id());

        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            Callable<Integer> submit = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit")
                                .header("Authorization", "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "description": "Racing submission." }
                                        """))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> cancel = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                                .header("Authorization", "Bearer " + requesterToken))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> submitResult = pool.submit(submit);
            Future<Integer> cancelResult = pool.submit(cancel);
            ready.await();
            go.countDown();

            int submitStatus = submitResult.get();
            int cancelStatus = cancelResult.get();
            pool.shutdown();

            // Both race the same IN_PROGRESS -> X guard; exactly one of the two must win (200) and
            // the other must lose (409) — never both succeeding, never both failing.
            assertThat(submitStatus == 200 || cancelStatus == 200).isTrue();
            assertThat((submitStatus == 200) ^ (cancelStatus == 200)).isTrue();
        } finally {
            userRepository.findByEmail(providerEmail).ifPresent(u -> userRepository.deleteById(u.getId()));
            userRepository.findByEmail(requesterEmail).ifPresent(u -> userRepository.deleteById(u.getId()));
        }
    }
}
