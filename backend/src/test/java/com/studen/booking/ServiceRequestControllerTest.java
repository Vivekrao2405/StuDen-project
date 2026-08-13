package com.studen.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.marketplace.ServiceResponse;
import com.studen.notification.RecordingNotifier;
import com.studen.user.UserRepository;
import java.util.List;
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
// See ServiceListingControllerTest/AuthControllerTest for why the auth rate limit needs raising:
// AuthRateLimitFilter's per-IP counter is shared (and accumulates) across every @SpringBootTest in
// this JVM run. app.booking.max-requests-per-window is lowered so the 429 test doesn't need to
// create dozens of requests.
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-requests=100000",
        "app.booking.max-requests-per-window=2",
        "app.booking.rate-limit-window-minutes=60"
})
class ServiceRequestControllerTest {

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
    // committed to the shared dev database.
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

    private ServiceResponse createService(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "%s", "category": "TECHNOLOGY" }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ServiceResponse.class);
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

    private String requestPayload(String serviceId) {
        return """
                { "serviceId": "%s", "description": "I need a dashboard for my college project, please help." }
                """.formatted(serviceId);
    }

    private ServiceRequestResponse createRequestAndReturn(String requesterToken, String serviceId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(serviceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ServiceRequestResponse.class);
    }

    // --- Happy path -----------------------------------------------------------------------

    @Test
    void createRequest_forActiveAvailableService_startsPending() throws Exception {
        String providerToken = registerWithPortfolio("request-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Power BI Dashboard Development");
        String requesterToken = registerWithPortfolio("request-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(service.id().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.serviceTitle").value("Power BI Dashboard Development"))
                .andExpect(jsonPath("$.providerName").value("Test User"));
    }

    @Test
    void createRequest_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/service-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(java.util.UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequest_ownService_returns400() throws Exception {
        String token = registerWithPortfolio("request-own-service@example.com");
        ServiceResponse service = createAndPublishService(token, "My Own Service");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(service.id().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createRequest_forDraftService_returns404() throws Exception {
        String providerToken = registerWithPortfolio("request-draft-provider@example.com");
        ServiceResponse draft = createService(providerToken, "Draft Service");
        String requesterToken = registerWithPortfolio("request-draft-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(draft.id().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequest_forInactiveService_returns404() throws Exception {
        String providerToken = registerWithPortfolio("request-inactive-provider@example.com");
        ServiceResponse published = createAndPublishService(providerToken, "Inactive Service");
        mockMvc.perform(post("/api/v1/users/me/services/" + published.id() + "/deactivate")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());
        String requesterToken = registerWithPortfolio("request-inactive-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(published.id().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRequest_forUnavailableService_returns404() throws Exception {
        String providerToken = registerWithPortfolio("request-unavailable-provider@example.com");
        ServiceResponse published = createAndPublishService(providerToken, "Unavailable Service");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/me/services/" + published.id() + "/availability")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "available": false }
                                """))
                .andExpect(status().isOk());
        String requesterToken = registerWithPortfolio("request-unavailable-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(published.id().toString())))
                .andExpect(status().isNotFound());
    }

    // --- Duplicate / rate limit -------------------------------------------------------------

    @Test
    void createRequest_duplicatePending_returns409() throws Exception {
        String providerToken = registerWithPortfolio("request-dup-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Duplicate Check Service");
        String requesterToken = registerWithPortfolio("request-dup-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(service.id().toString())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(service.id().toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void createRequest_exceedingRateLimitWindow_returns429() throws Exception {
        String providerToken = registerWithPortfolio("request-rate-provider@example.com");
        ServiceResponse serviceA = createAndPublishService(providerToken, "Rate Limit Service A");
        ServiceResponse serviceB = createAndPublishService(providerToken, "Rate Limit Service B");
        ServiceResponse serviceC = createAndPublishService(providerToken, "Rate Limit Service C");
        String requesterToken = registerWithPortfolio("request-rate-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(serviceA.id().toString())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(serviceB.id().toString())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(serviceC.id().toString())))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    // --- Validation -------------------------------------------------------------------------

    @Test
    void createRequest_tooShortDescription_returns400() throws Exception {
        String providerToken = registerWithPortfolio("request-shortdesc-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Short Desc Service");
        String requesterToken = registerWithPortfolio("request-shortdesc-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceId": "%s", "description": "too short" }
                                """.formatted(service.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createRequest_negativeBudget_returns400() throws Exception {
        String providerToken = registerWithPortfolio("request-negbudget-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Negative Budget Service");
        String requesterToken = registerWithPortfolio("request-negbudget-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "description": "I need a dashboard for my college project, please help.",
                                  "proposedBudget": -100
                                }
                                """.formatted(service.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createRequest_pastDeliveryDate_returns400() throws Exception {
        String providerToken = registerWithPortfolio("request-pastdate-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Past Date Service");
        String requesterToken = registerWithPortfolio("request-pastdate-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "description": "I need a dashboard for my college project, please help.",
                                  "requestedDeliveryDate": "2000-01-01"
                                }
                                """.formatted(service.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createRequest_withDangerousLinkScheme_returns400() throws Exception {
        String providerToken = registerWithPortfolio("request-danger-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Dangerous Link Service");
        String requesterToken = registerWithPortfolio("request-danger-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "description": "I need a dashboard for my college project, please help.",
                                  "links": [ { "label": "Bad", "url": "javascript:alert(1)" } ]
                                }
                                """.formatted(service.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- Ownership / privacy ------------------------------------------------------------------

    @Test
    void getRequest_forUnrelatedUser_returns404() throws Exception {
        String providerToken = registerWithPortfolio("request-unrelated-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Unrelated Service");
        String requesterToken = registerWithPortfolio("request-unrelated-requester@example.com");

        String body = mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(service.id().toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(body).get("id").asText();

        String outsiderToken = registerWithPortfolio("request-unrelated-outsider@example.com");
        mockMvc.perform(get("/api/v1/service-requests/" + requestId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRequest_neverExposesEmailOrInternalIds() throws Exception {
        String providerToken = registerWithPortfolio("request-privacy-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Privacy Check Service");
        String requesterToken = registerWithPortfolio("request-privacy-requester@example.com");

        String body = mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(service.id().toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("request-privacy-provider@example.com");
        assertThat(body).doesNotContainIgnoringCase("request-privacy-requester@example.com");
        assertThat(body).doesNotContainIgnoringCase("\"email\"");
        assertThat(body).doesNotContainIgnoringCase("password");
        assertThat(body).doesNotContainIgnoringCase("requesterId");
        assertThat(body).doesNotContainIgnoringCase("providerId");
    }

    @Test
    void incomingRequests_onlyShowsRequestsForOwnServices() throws Exception {
        String providerAToken = registerWithPortfolio("request-incoming-a@example.com");
        ServiceResponse serviceA = createAndPublishService(providerAToken, "Provider A Service");
        String providerBToken = registerWithPortfolio("request-incoming-b@example.com");
        createAndPublishService(providerBToken, "Provider B Service");
        String requesterToken = registerWithPortfolio("request-incoming-requester@example.com");

        mockMvc.perform(post("/api/v1/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(serviceA.id().toString())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/service-requests/incoming")
                        .header("Authorization", "Bearer " + providerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceTitle").value("Provider A Service"));

        mockMvc.perform(get("/api/v1/service-requests/incoming")
                        .header("Authorization", "Bearer " + providerBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Regression for the And/Or derived-query-name pitfall: findByIdAndRequesterIdOrProviderId
    // must filter by the path id even on the "you're the provider" branch — a naive derived
    // method name parses as (id = ? AND requesterId = ?) OR providerId = ?, which drops the id
    // filter from the second clause and either 500s (NonUniqueResultException, 2+ own requests)
    // or silently returns the wrong request (exactly 1 other own request).
    @Test
    void getRequest_forProviderWithMultipleIncomingRequests_returnsTheExactRequestedOne() throws Exception {
        String providerToken = registerWithPortfolio("getrequest-multi-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Multi Request Service");
        String requesterAToken = registerWithPortfolio("getrequest-multi-requester-a@example.com");
        String requesterBToken = registerWithPortfolio("getrequest-multi-requester-b@example.com");

        ServiceRequestResponse requestA = createRequestAndReturn(requesterAToken, service.id().toString());
        ServiceRequestResponse requestB = createRequestAndReturn(requesterBToken, service.id().toString());

        mockMvc.perform(get("/api/v1/service-requests/" + requestA.id())
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestA.id().toString()))
                .andExpect(jsonPath("$.requesterName").value("Test User"));

        mockMvc.perform(get("/api/v1/service-requests/" + requestB.id())
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestB.id().toString()));
    }

    // --- Accept / Reject --------------------------------------------------------------------

    @Test
    void acceptRequest_byOwningProvider_setsAccepted() throws Exception {
        String providerToken = registerWithPortfolio("accept-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Acceptable Service");
        String requesterToken = registerWithPortfolio("accept-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
    }

    @Test
    void rejectRequest_byOwningProvider_setsRejectedWithReason() throws Exception {
        String providerToken = registerWithPortfolio("reject-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Rejectable Service");
        String requesterToken = registerWithPortfolio("reject-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "I don't have availability for this project right now." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectedAt").isNotEmpty())
                .andExpect(jsonPath("$.rejectionReason").value("I don't have availability for this project right now."));
    }

    @Test
    void rejectRequest_withoutReasonOrBody_succeeds() throws Exception {
        String providerToken = registerWithPortfolio("reject-noreason-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "No Reason Service");
        String requesterToken = registerWithPortfolio("reject-noreason-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void acceptRequest_byNonOwningProvider_returns404() throws Exception {
        String providerToken = registerWithPortfolio("accept-idor-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "IDOR Accept Service");
        String requesterToken = registerWithPortfolio("accept-idor-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        String otherProviderToken = registerWithPortfolio("accept-idor-other-provider@example.com");
        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + otherProviderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectRequest_byNonOwningProvider_returns404() throws Exception {
        String providerToken = registerWithPortfolio("reject-idor-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "IDOR Reject Service");
        String requesterToken = registerWithPortfolio("reject-idor-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        String otherProviderToken = registerWithPortfolio("reject-idor-other-provider@example.com");
        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + otherProviderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptRequest_byRequesterThemselves_returns404() throws Exception {
        String providerToken = registerWithPortfolio("accept-self-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Self Accept Service");
        String requesterToken = registerWithPortfolio("accept-self-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectRequest_byRequesterThemselves_returns404() throws Exception {
        String providerToken = registerWithPortfolio("reject-self-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Self Reject Service");
        String requesterToken = registerWithPortfolio("reject-self-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptRequest_alreadyAccepted_returns409() throws Exception {
        String providerToken = registerWithPortfolio("accept-twice-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Accept Twice Service");
        String requesterToken = registerWithPortfolio("accept-twice-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void rejectRequest_alreadyRejected_returns409() throws Exception {
        String providerToken = registerWithPortfolio("reject-twice-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Reject Twice Service");
        String requesterToken = registerWithPortfolio("reject-twice-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void rejectRequest_afterAccepted_returns409() throws Exception {
        String providerToken = registerWithPortfolio("accept-then-reject-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Accept Then Reject Service");
        String requesterToken = registerWithPortfolio("accept-then-reject-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptRequest_afterRejected_returns409() throws Exception {
        String providerToken = registerWithPortfolio("reject-then-accept-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Reject Then Accept Service");
        String requesterToken = registerWithPortfolio("reject-then-accept-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptRequest_againstDeactivatedService_returns409() throws Exception {
        // The service must have been ACTIVE at request-creation time (createRequest enforces
        // that), so simulate "went invalid after the request was made" by deactivating it
        // afterward — accept must then refuse, reject must still work (see next test).
        String providerToken = registerWithPortfolio("accept-deactivated-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Later Deactivated Service");
        String requesterToken = registerWithPortfolio("accept-deactivated-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/users/me/services/" + service.id() + "/deactivate")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectRequest_againstDeactivatedService_stillSucceeds() throws Exception {
        String providerToken = registerWithPortfolio("reject-deactivated-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Reject Deactivated Service");
        String requesterToken = registerWithPortfolio("reject-deactivated-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        mockMvc.perform(post("/api/v1/users/me/services/" + service.id() + "/deactivate")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejectRequest_reasonTooLong_returns400() throws Exception {
        String providerToken = registerWithPortfolio("reject-longreason-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Long Reason Service");
        String requesterToken = registerWithPortfolio("reject-longreason-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        String tooLong = "x".repeat(501);
        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectServiceRequestRequest(tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- Notifications ------------------------------------------------------------------------

    @Test
    void acceptRequest_notifiesRequester() throws Exception {
        String providerToken = registerWithPortfolio("notify-accept-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Notify Accept Service");
        String requesterToken = registerWithPortfolio("notify-accept-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        recordingNotifier.clear();
        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        assertThat(recordingNotifier.all()).anyMatch(n -> n.message().contains("was accepted by"));
    }

    @Test
    void rejectRequest_notifiesRequester() throws Exception {
        String providerToken = registerWithPortfolio("notify-reject-provider@example.com");
        ServiceResponse service = createAndPublishService(providerToken, "Notify Reject Service");
        String requesterToken = registerWithPortfolio("notify-reject-requester@example.com");
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        recordingNotifier.clear();
        mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        assertThat(recordingNotifier.all()).anyMatch(n -> n.message().contains("was not accepted by"));
    }

    // --- Concurrency ----------------------------------------------------------------------

    // Overrides the class-level @Transactional with NOT_SUPPORTED so setup data actually commits
    // (an outer test-rollback transaction never commits, so background threads on separate
    // connections would never see it under READ_COMMITTED) and so the two concurrent calls below
    // run in genuinely separate database transactions — the only way to honestly exercise
    // acceptIfPending/rejectIfPending's atomic-UPDATE race guard. Distinct full names + a random
    // email suffix + the finally-block cleanup keep this the one test in the class that's allowed
    // to actually commit without leaving residue in the shared dev database for other tests
    // (verified the hard way: an earlier version of this test used "Test User"/fixed emails and
    // both polluted PortfolioControllerTest's slug-uniqueness assertion and broke on rerun with a
    // duplicate-email 409).
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void acceptAndReject_calledConcurrently_onlyOneTransitionSucceeds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String providerEmail = "race-provider-" + suffix + "@example.com";
        String requesterEmail = "race-requester-" + suffix + "@example.com";
        String providerToken = registerWithPortfolio("Race Provider", providerEmail);
        ServiceResponse service = createAndPublishService(providerToken, "Race Condition Service");
        String requesterToken = registerWithPortfolio("Race Requester", requesterEmail);
        ServiceRequestResponse created = createRequestAndReturn(requesterToken, service.id().toString());

        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            Callable<Integer> acceptCall = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/accept")
                                .header("Authorization", "Bearer " + providerToken))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> rejectCall = () -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/service-requests/" + created.id() + "/reject")
                                .header("Authorization", "Bearer " + providerToken))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> acceptResult = pool.submit(acceptCall);
            Future<Integer> rejectResult = pool.submit(rejectCall);
            ready.await();
            go.countDown();

            int acceptStatus = acceptResult.get();
            int rejectStatus = rejectResult.get();
            pool.shutdown();

            assertThat(List.of(acceptStatus, rejectStatus)).containsExactlyInAnyOrder(200, 409);

            String finalBody = mockMvc.perform(get("/api/v1/service-requests/" + created.id())
                            .header("Authorization", "Bearer " + providerToken))
                    .andReturn().getResponse().getContentAsString();
            String finalStatus = objectMapper.readTree(finalBody).get("status").asText();
            assertThat(finalStatus).isIn("ACCEPTED", "REJECTED");
        } finally {
            // Cascades (users -> student_portfolios -> services -> service_requests) handle the rest.
            userRepository.findByEmail(providerEmail).ifPresent(u -> userRepository.deleteById(u.getId()));
            userRepository.findByEmail(requesterEmail).ifPresent(u -> userRepository.deleteById(u.getId()));
        }
    }
}
