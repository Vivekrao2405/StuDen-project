package com.studen.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.marketplace.ServiceResponse;
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
}
