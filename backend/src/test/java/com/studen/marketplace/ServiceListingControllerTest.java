package com.studen.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// See AuthControllerTest for why: AuthRateLimitFilter's per-IP counter is shared (and
// accumulates) across every @SpringBootTest in this JVM run.
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-requests=100000",
        "app.service.max-services-per-student=3",
        "app.service.max-images-per-service=2",
        "app.service.max-videos-per-service=1"
})
class ServiceListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    private byte[] mp4Bytes() {
        return new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    /** Registers a fresh user, creates their portfolio, and returns the token — every service
     * test needs a portfolio to attach services to, matching the app's real flow. */
    private String registerWithPortfolio(String email) throws Exception {
        String token = registerAndGetToken(email);
        String payload = """
                {
                  "headline": "Test Student",
                  "location": "Hyderabad",
                  "available": true
                }
                """;
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
        return token;
    }

    private ServiceResponse createService(String token, String title) throws Exception {
        String payload = """
                {
                  "title": "%s",
                  "category": "TECHNOLOGY"
                }
                """.formatted(title);

        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ServiceResponse.class);
    }

    private String publishablePayload(String title) {
        return """
                {
                  "title": "%s",
                  "category": "TECHNOLOGY",
                  "description": "A full description of what this service offers.",
                  "priceAmount": 1500,
                  "deliveryDays": 3
                }
                """.formatted(title);
    }

    private ServiceResponse createAndPublishService(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishablePayload(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ServiceResponse created = objectMapper.readValue(body, ServiceResponse.class);

        String publishedBody = mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(publishedBody, ServiceResponse.class);
    }

    private String createProjectId(String token, String title, boolean isPublic) throws Exception {
        String payload = """
                {
                  "title": "%s",
                  "visibility": "%s"
                }
                """.formatted(title, isPublic ? "PUBLIC" : "PRIVATE");
        String body = mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    // --- Auth -----------------------------------------------------------------------------

    @Test
    void getMyServices_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/services"))
                .andExpect(status().isUnauthorized());
    }

    // --- CRUD / lifecycle --------------------------------------------------------------------

    @Test
    void createService_startsAsDraft() throws Exception {
        String token = registerWithPortfolio("service-create@example.com");
        ServiceResponse created = createService(token, "Power BI Dashboard Development");

        assertThat(created.status()).isEqualTo(ServiceStatus.DRAFT);
        assertThat(created.title()).isEqualTo("Power BI Dashboard Development");
    }

    @Test
    void createService_withoutTitleOrCategory_returns400() throws Exception {
        String token = registerWithPortfolio("service-notitle@example.com");

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createService_beyondMaxPerStudent_returns400() throws Exception {
        String token = registerWithPortfolio("service-limit@example.com");
        createService(token, "One");
        createService(token, "Two");
        createService(token, "Three");

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Four", "category": "TECHNOLOGY" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void updateService_changesFields() throws Exception {
        String token = registerWithPortfolio("service-update@example.com");
        ServiceResponse created = createService(token, "Original Title");

        mockMvc.perform(put("/api/v1/users/me/services/" + created.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Updated Title", "category": "DESIGN_CREATIVE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.category").value("DESIGN_CREATIVE"));
    }

    @Test
    void updateService_onActiveService_clearingPrice_returns400() throws Exception {
        String token = registerWithPortfolio("service-update-active@example.com");
        ServiceResponse published = createAndPublishService(token, "Live Service");

        mockMvc.perform(put("/api/v1/users/me/services/" + published.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Live Service",
                                  "category": "TECHNOLOGY",
                                  "description": "Still has a description",
                                  "deliveryDays": 3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void publishService_withAllRequiredFields_setsActive() throws Exception {
        String token = registerWithPortfolio("service-publish@example.com");
        ServiceResponse published = createAndPublishService(token, "Publishable Service");

        assertThat(published.status()).isEqualTo(ServiceStatus.ACTIVE);
    }

    @Test
    void publishService_missingPrice_returns400() throws Exception {
        String token = registerWithPortfolio("service-publish-noprice@example.com");
        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "No Price Service",
                                  "category": "TECHNOLOGY",
                                  "description": "Has a description",
                                  "deliveryDays": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ServiceResponse created = objectMapper.readValue(body, ServiceResponse.class);

        mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void publishService_missingDeliveryDays_returns400() throws Exception {
        String token = registerWithPortfolio("service-publish-nodelivery@example.com");
        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "No Delivery Service",
                                  "category": "TECHNOLOGY",
                                  "description": "Has a description",
                                  "priceAmount": 1000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ServiceResponse created = objectMapper.readValue(body, ServiceResponse.class);

        mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void deactivateService_hidesFromMarketplaceSearchButStaysOwnerVisible() throws Exception {
        String token = registerWithPortfolio("service-deactivate@example.com");
        ServiceResponse published = createAndPublishService(token, "Deactivate Me Service");

        mockMvc.perform(post("/api/v1/users/me/services/" + published.id() + "/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/marketplace").param("q", "Deactivate Me Service")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Deactivate Me Service"));
    }

    @Test
    void deactivateService_fromDraft_returns400() throws Exception {
        String token = registerWithPortfolio("service-deactivate-draft@example.com");
        ServiceResponse created = createService(token, "Draft Service");

        mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void reactivateService_fromInactive_setsActiveAgain() throws Exception {
        String token = registerWithPortfolio("service-reactivate@example.com");
        ServiceResponse published = createAndPublishService(token, "Reactivate Me Service");
        mockMvc.perform(post("/api/v1/users/me/services/" + published.id() + "/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/me/services/" + published.id() + "/reactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void reactivateService_fromDraft_returns400() throws Exception {
        String token = registerWithPortfolio("service-reactivate-draft@example.com");
        ServiceResponse created = createService(token, "Draft Reactivate Service");

        mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/reactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void setAvailability_togglesFlagWithoutChangingStatus() throws Exception {
        String token = registerWithPortfolio("service-availability@example.com");
        ServiceResponse published = createAndPublishService(token, "Availability Toggle Service");

        mockMvc.perform(put("/api/v1/users/me/services/" + published.id() + "/availability")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "available": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteService_removesItFromList() throws Exception {
        String token = registerWithPortfolio("service-delete@example.com");
        ServiceResponse created = createService(token, "Delete Me");

        mockMvc.perform(delete("/api/v1/users/me/services/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/services").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteService_doesNotDeleteLinkedShowcaseProject() throws Exception {
        String token = registerWithPortfolio("service-delete-linked@example.com");
        String projectId = createProjectId(token, "Linked Project", true);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Service With Linked Project");
        payload.put("category", "TECHNOLOGY");
        payload.putArray("linkedProjectIds").add(projectId);

        String body = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ServiceResponse created = objectMapper.readValue(body, ServiceResponse.class);
        assertThat(created.linkedProjects()).hasSize(1);

        mockMvc.perform(delete("/api/v1/users/me/services/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId));
    }

    // --- Media ----------------------------------------------------------------------------

    @Test
    void uploadMedia_image_becomesFirstAndAutoCover() throws Exception {
        String token = registerWithPortfolio("service-media-image@example.com");
        ServiceResponse service = createService(token, "With Image");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.cover").value(true))
                .andExpect(jsonPath("$.url").isNotEmpty());
    }

    @Test
    void uploadMedia_video_getsThumbnailUrl() throws Exception {
        String token = registerWithPortfolio("service-media-video@example.com");
        ServiceResponse service = createService(token, "With Video");

        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4Bytes());

        mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.thumbnailUrl").isNotEmpty());
    }

    @Test
    void uploadMedia_invalidVideoContent_returns400() throws Exception {
        String token = registerWithPortfolio("service-media-badvideo@example.com");
        ServiceResponse service = createService(token, "Bad Video");

        MockMultipartFile file = new MockMultipartFile("file", "fake.mp4", "video/mp4", "not a video".getBytes());

        mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void uploadMedia_beyondMaxImagesPerService_returns400() throws Exception {
        String token = registerWithPortfolio("service-media-imagelimit@example.com");
        ServiceResponse service = createService(token, "Many Images");

        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "photo" + i + ".jpg", "image/jpeg", jpegBytes());
            mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        MockMultipartFile third = new MockMultipartFile("file", "photo3.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                        .file(third)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void removeMedia_deletesIt() throws Exception {
        String token = registerWithPortfolio("service-media-remove@example.com");
        ServiceResponse service = createService(token, "Remove Media");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        String body = mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ServiceMediaResponse uploaded = objectMapper.readValue(body, ServiceMediaResponse.class);

        mockMvc.perform(delete("/api/v1/users/me/services/" + service.id() + "/media/" + uploaded.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(0));
    }

    @Test
    void reorderMedia_appliesNewOrder() throws Exception {
        String token = registerWithPortfolio("service-media-reorder@example.com");
        ServiceResponse service = createService(token, "Reorder Media");

        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "photo" + i + ".jpg", "image/jpeg", jpegBytes());
            mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        String body = mockMvc.perform(get("/api/v1/users/me/services").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        List<ServiceResponse> services = List.of(objectMapper.readValue(body, ServiceResponse[].class));
        ServiceResponse withMedia = services.get(0);
        var firstId = withMedia.media().get(0).id();
        var secondId = withMedia.media().get(1).id();

        String reorderPayload = objectMapper.writeValueAsString(new UpdateServiceMediaOrderRequest(List.of(secondId, firstId)));
        mockMvc.perform(put("/api/v1/users/me/services/" + service.id() + "/media/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].id").value(secondId.toString()))
                .andExpect(jsonPath("$.media[1].id").value(firstId.toString()));
    }

    @Test
    void setCoverMedia_changesCover() throws Exception {
        String token = registerWithPortfolio("service-media-cover@example.com");
        ServiceResponse service = createService(token, "Cover Media");

        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "photo" + i + ".jpg", "image/jpeg", jpegBytes());
            mockMvc.perform(multipart("/api/v1/users/me/services/" + service.id() + "/media")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        String body = mockMvc.perform(get("/api/v1/users/me/services").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        List<ServiceResponse> services = List.of(objectMapper.readValue(body, ServiceResponse[].class));
        var secondId = services.get(0).media().get(1).id();

        mockMvc.perform(put("/api/v1/users/me/services/" + service.id() + "/media/" + secondId + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].cover").value(false))
                .andExpect(jsonPath("$.media[1].cover").value(true));
    }

    // --- Ownership / IDOR --------------------------------------------------------------------

    @Test
    void updateService_forAnotherStudentsService_returns404() throws Exception {
        String tokenA = registerWithPortfolio("service-idor-a@example.com");
        String tokenB = registerWithPortfolio("service-idor-b@example.com");
        ServiceResponse serviceA = createService(tokenA, "Student A's Service");

        mockMvc.perform(put("/api/v1/users/me/services/" + serviceA.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Hijacked", "category": "TECHNOLOGY" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteService_forAnotherStudentsService_returns404() throws Exception {
        String tokenA = registerWithPortfolio("service-idor-delete-a@example.com");
        String tokenB = registerWithPortfolio("service-idor-delete-b@example.com");
        ServiceResponse serviceA = createService(tokenA, "Student A's Service");

        mockMvc.perform(delete("/api/v1/users/me/services/" + serviceA.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadMedia_toAnotherStudentsService_returns404() throws Exception {
        String tokenA = registerWithPortfolio("service-idor-media-a@example.com");
        String tokenB = registerWithPortfolio("service-idor-media-b@example.com");
        ServiceResponse serviceA = createService(tokenA, "Student A's Service");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/users/me/services/" + serviceA.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void createService_linkingAnotherStudentsProject_returns400() throws Exception {
        String tokenA = registerWithPortfolio("service-idor-link-a@example.com");
        String tokenB = registerWithPortfolio("service-idor-link-b@example.com");
        String projectAId = createProjectId(tokenA, "Student A's Project", true);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Student B's Service");
        payload.put("category", "TECHNOLOGY");
        payload.putArray("linkedProjectIds").add(projectAId);

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createService_linkingOwnPrivateProject_returns400() throws Exception {
        String token = registerWithPortfolio("service-private-link@example.com");
        String privateProjectId = createProjectId(token, "My Private Project", false);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Service Linking Private Project");
        payload.put("category", "TECHNOLOGY");
        payload.putArray("linkedProjectIds").add(privateProjectId);

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- Links / URL validation -----------------------------------------------------------

    @Test
    void createService_withDangerousLinkScheme_returns400() throws Exception {
        String token = registerWithPortfolio("service-link-danger@example.com");

        String payload = """
                {
                  "title": "Dangerous Link Service",
                  "category": "TECHNOLOGY",
                  "links": [ { "label": "Bad", "url": "javascript:alert(1)" } ]
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createService_withValidLinks_savesThem() throws Exception {
        String token = registerWithPortfolio("service-link-valid@example.com");

        String payload = """
                {
                  "title": "Linked Service",
                  "category": "TECHNOLOGY",
                  "links": [
                    { "label": "GitHub", "url": "https://github.com/example/repo" },
                    { "label": "Website", "url": "https://example.com" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].label").value("GitHub"));
    }

    // --- Skills -----------------------------------------------------------------------------

    @Test
    void createService_withSkills_attachesThem() throws Exception {
        String token = registerWithPortfolio("service-skills@example.com");

        String skillsBody = mockMvc.perform(get("/api/v1/skills/search").param("q", "react")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        var skills = objectMapper.readTree(skillsBody);

        String payload = """
                {
                  "title": "Skilled Service",
                  "category": "TECHNOLOGY",
                  "skillIds": ["%s"]
                }
                """.formatted(skills.get(0).get("id").asText());

        mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].name").value("React"));
    }

    // --- Public visibility --------------------------------------------------------------------

    @Test
    void getPublicService_forActiveService_returnsDetail() throws Exception {
        String token = registerWithPortfolio("service-public-active@example.com");
        ServiceResponse published = createAndPublishService(token, "Public Active Service");

        mockMvc.perform(get("/api/v1/public/services/" + published.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Public Active Service"))
                .andExpect(jsonPath("$.providerSlug").isNotEmpty());
    }

    @Test
    void getPublicService_forDraftService_returns404() throws Exception {
        String token = registerWithPortfolio("service-public-draft@example.com");
        ServiceResponse created = createService(token, "Public Draft Service");

        mockMvc.perform(get("/api/v1/public/services/" + created.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicService_forInactiveService_returns404() throws Exception {
        String token = registerWithPortfolio("service-public-inactive@example.com");
        ServiceResponse published = createAndPublishService(token, "Public Inactive Service");
        mockMvc.perform(post("/api/v1/users/me/services/" + published.id() + "/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/services/" + published.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicService_neverExposesEmail() throws Exception {
        String token = registerWithPortfolio("service-public-privacy@example.com");
        ServiceResponse published = createAndPublishService(token, "Privacy Check Service");

        String body = mockMvc.perform(get("/api/v1/public/services/" + published.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("service-public-privacy@example.com");
        assertThat(body).doesNotContainIgnoringCase("\"email\"");
        assertThat(body).doesNotContainIgnoringCase("password");
    }

    @Test
    void getPublicService_includesProviderHeadline() throws Exception {
        String token = registerWithPortfolio("service-public-headline@example.com");
        ServiceResponse published = createAndPublishService(token, "Headline Check Service");

        mockMvc.perform(get("/api/v1/public/services/" + published.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerHeadline").value("Test Student"));
    }

    @Test
    void getPublicService_projectMadePrivateAfterLinking_isExcludedFromLinkedProjects() throws Exception {
        String token = registerWithPortfolio("service-linked-then-private@example.com");
        String publicProjectId = createProjectId(token, "Was Public Project", true);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Service With Later-Private Project");
        payload.put("category", "TECHNOLOGY");
        payload.put("description", "A full description of what this service offers.");
        payload.put("priceAmount", 1500);
        payload.put("deliveryDays", 3);
        payload.putArray("linkedProjectIds").add(publicProjectId);

        String createdBody = mockMvc.perform(post("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkedProjects.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        ServiceResponse created = objectMapper.readValue(createdBody, ServiceResponse.class);

        mockMvc.perform(post("/api/v1/users/me/services/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Flip the linked project to PRIVATE after it was linked while PUBLIC.
        String updateProjectPayload = """
                {
                  "title": "Was Public Project",
                  "visibility": "PRIVATE"
                }
                """;
        mockMvc.perform(put("/api/v1/users/me/projects/" + publicProjectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProjectPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/services/" + created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedProjects.length()").value(0));

        // The owner's own view must still show it — only the public response hides it.
        String myServicesBody = mockMvc.perform(get("/api/v1/users/me/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<ServiceResponse> myServices = objectMapper.readValue(myServicesBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ServiceResponse.class));
        ServiceResponse owned = myServices.stream().filter(s -> s.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(owned.linkedProjects()).hasSize(1);
    }
}
