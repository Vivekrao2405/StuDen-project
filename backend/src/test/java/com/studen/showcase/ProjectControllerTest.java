package com.studen.showcase;

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
import com.studen.portfolio.PortfolioResponse;
import com.studen.skill.SkillResponse;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// See AuthControllerTest for why: AuthRateLimitFilter's per-IP counter is shared (and
// accumulates) across every @SpringBootTest in this JVM run.
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-requests=100000",
        "app.project.max-projects-per-student=3",
        "app.project.max-images-per-project=2",
        "app.project.max-videos-per-project=1"
})
class ProjectControllerTest {

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

    /** Registers a fresh user, creates their portfolio, and returns the token — every project
     * test needs a portfolio to attach projects to, matching the app's real flow. */
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

    private ProjectResponse createProject(String token, String title) throws Exception {
        return createProject(token, title, "PUBLIC");
    }

    private ProjectResponse createProject(String token, String title, String visibility) throws Exception {
        String payload = """
                {
                  "title": "%s",
                  "shortDescription": "A short description",
                  "visibility": "%s"
                }
                """.formatted(title, visibility);

        String body = mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ProjectResponse.class);
    }

    // --- Auth -----------------------------------------------------------------------------

    @Test
    void getMyProjects_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/projects"))
                .andExpect(status().isUnauthorized());
    }

    // --- CRUD -----------------------------------------------------------------------------

    @Test
    void createProject_thenListIncludesIt() throws Exception {
        String token = registerWithPortfolio("project-create@example.com");
        createProject(token, "AI Recipe Maker");

        mockMvc.perform(get("/api/v1/users/me/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("AI Recipe Maker"))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[0].media").isArray())
                .andExpect(jsonPath("$[0].media.length()").value(0));
    }

    @Test
    void createProject_withoutTitle_returns400() throws Exception {
        String token = registerWithPortfolio("project-notitle@example.com");

        mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createProject_beyondMaxPerStudent_returns400() throws Exception {
        String token = registerWithPortfolio("project-limit@example.com");
        createProject(token, "One");
        createProject(token, "Two");
        createProject(token, "Three");

        mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Four" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void updateProject_changesFieldsAndVisibility() throws Exception {
        String token = registerWithPortfolio("project-update@example.com");
        ProjectResponse created = createProject(token, "Original Title");

        mockMvc.perform(put("/api/v1/users/me/projects/" + created.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Updated Title", "visibility": "PRIVATE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void deleteProject_removesItFromList() throws Exception {
        String token = registerWithPortfolio("project-delete@example.com");
        ProjectResponse created = createProject(token, "Delete Me");

        mockMvc.perform(delete("/api/v1/users/me/projects/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Media ----------------------------------------------------------------------------

    @Test
    void uploadMedia_image_becomesFirstAndAutoCover() throws Exception {
        String token = registerWithPortfolio("project-media-image@example.com");
        ProjectResponse project = createProject(token, "With Image");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.media[0].cover").value(true))
                .andExpect(jsonPath("$.coverImageUrl").isNotEmpty());
    }

    @Test
    void uploadMedia_video_getsThumbnailUrl() throws Exception {
        String token = registerWithPortfolio("project-media-video@example.com");
        ProjectResponse project = createProject(token, "With Video");

        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4Bytes());

        mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.media[0].thumbnailUrl").isNotEmpty());
    }

    @Test
    void uploadMedia_invalidVideoContent_returns400() throws Exception {
        String token = registerWithPortfolio("project-media-badvideo@example.com");
        ProjectResponse project = createProject(token, "Bad Video");

        MockMultipartFile file = new MockMultipartFile("file", "fake.mp4", "video/mp4", "not a video".getBytes());

        mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void uploadMedia_beyondMaxImagesPerProject_returns400() throws Exception {
        String token = registerWithPortfolio("project-media-imagelimit@example.com");
        ProjectResponse project = createProject(token, "Many Images");

        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "photo" + i + ".jpg", "image/jpeg", jpegBytes());
            mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        MockMultipartFile third = new MockMultipartFile("file", "photo3.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                        .file(third)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void removeMedia_deletesIt() throws Exception {
        String token = registerWithPortfolio("project-media-remove@example.com");
        ProjectResponse project = createProject(token, "Remove Media");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        String body = mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ProjectResponse withMedia = objectMapper.readValue(body, ProjectResponse.class);
        var mediaId = withMedia.media().get(0).id();

        mockMvc.perform(delete("/api/v1/users/me/projects/" + project.id() + "/media/" + mediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(0));
    }

    @Test
    void reorderMedia_appliesNewOrder() throws Exception {
        String token = registerWithPortfolio("project-media-reorder@example.com");
        ProjectResponse project = createProject(token, "Reorder Media");

        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "photo" + i + ".jpg", "image/jpeg", jpegBytes());
            mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        String body = mockMvc.perform(get("/api/v1/users/me/projects").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        List<ProjectResponse> projects = List.of(objectMapper.readValue(body, ProjectResponse[].class));
        ProjectResponse withMedia = projects.get(0);
        var firstId = withMedia.media().get(0).id();
        var secondId = withMedia.media().get(1).id();

        String reorderPayload = objectMapper.writeValueAsString(new UpdateMediaOrderRequest(List.of(secondId, firstId)));
        mockMvc.perform(put("/api/v1/users/me/projects/" + project.id() + "/media/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].id").value(secondId.toString()))
                .andExpect(jsonPath("$.media[1].id").value(firstId.toString()));
    }

    @Test
    void setCoverMedia_changesCover() throws Exception {
        String token = registerWithPortfolio("project-media-cover@example.com");
        ProjectResponse project = createProject(token, "Cover Media");

        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("file", "photo" + i + ".jpg", "image/jpeg", jpegBytes());
            mockMvc.perform(multipart("/api/v1/users/me/projects/" + project.id() + "/media")
                            .file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        String body = mockMvc.perform(get("/api/v1/users/me/projects").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        List<ProjectResponse> projects = List.of(objectMapper.readValue(body, ProjectResponse[].class));
        var secondId = projects.get(0).media().get(1).id();

        mockMvc.perform(put("/api/v1/users/me/projects/" + project.id() + "/media/" + secondId + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].cover").value(false))
                .andExpect(jsonPath("$.media[1].cover").value(true));
    }

    // --- Ownership / IDOR --------------------------------------------------------------------

    @Test
    void updateProject_forAnotherStudentsProject_returns404() throws Exception {
        String tokenA = registerWithPortfolio("project-idor-a@example.com");
        String tokenB = registerWithPortfolio("project-idor-b@example.com");
        ProjectResponse projectA = createProject(tokenA, "Student A's Project");

        mockMvc.perform(put("/api/v1/users/me/projects/" + projectA.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Hijacked" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProject_forAnotherStudentsProject_returns404() throws Exception {
        String tokenA = registerWithPortfolio("project-idor-delete-a@example.com");
        String tokenB = registerWithPortfolio("project-idor-delete-b@example.com");
        ProjectResponse projectA = createProject(tokenA, "Student A's Project");

        mockMvc.perform(delete("/api/v1/users/me/projects/" + projectA.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadMedia_toAnotherStudentsProject_returns404() throws Exception {
        String tokenA = registerWithPortfolio("project-idor-media-a@example.com");
        String tokenB = registerWithPortfolio("project-idor-media-b@example.com");
        ProjectResponse projectA = createProject(tokenA, "Student A's Project");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/users/me/projects/" + projectA.id() + "/media")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    // --- Links / URL validation -----------------------------------------------------------

    @Test
    void createProject_withDangerousLinkScheme_returns400() throws Exception {
        String token = registerWithPortfolio("project-link-danger@example.com");

        String payload = """
                {
                  "title": "Dangerous Link Project",
                  "links": [ { "label": "Bad", "url": "javascript:alert(1)" } ]
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createProject_withValidLinks_savesThem() throws Exception {
        String token = registerWithPortfolio("project-link-valid@example.com");

        String payload = """
                {
                  "title": "Linked Project",
                  "links": [
                    { "label": "GitHub", "url": "https://github.com/example/repo" },
                    { "label": "Live Demo", "url": "https://example.com" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].label").value("GitHub"));
    }

    // --- Skills -----------------------------------------------------------------------------

    @Test
    void createProject_withSkills_attachesThem() throws Exception {
        String token = registerWithPortfolio("project-skills@example.com");

        String skillsBody = mockMvc.perform(get("/api/v1/skills/search").param("q", "react")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        SkillResponse[] skills = objectMapper.readValue(skillsBody, SkillResponse[].class);

        String payload = """
                {
                  "title": "Skilled Project",
                  "skillIds": ["%s"]
                }
                """.formatted(skills[0].id());

        mockMvc.perform(post("/api/v1/users/me/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].name").value("React"));
    }

    // --- Public visibility --------------------------------------------------------------------

    @Test
    void publicProfile_showsOnlyPublicProjects() throws Exception {
        String token = registerWithPortfolio("project-public-profile@example.com");
        createProject(token, "Public One", "PUBLIC");
        createProject(token, "Private One", "PRIVATE");

        String meBody = mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String slug = objectMapper.readValue(meBody, PortfolioResponse.class).publicSlug();

        mockMvc.perform(get("/api/v1/public/profiles/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showcase.length()").value(1))
                .andExpect(jsonPath("$.showcase[0].title").value("Public One"));
    }

    @Test
    void getPublicProject_forPublicProject_returnsDetail() throws Exception {
        String token = registerWithPortfolio("project-public-detail@example.com");
        ProjectResponse project = createProject(token, "Public Detail Project", "PUBLIC");

        mockMvc.perform(get("/api/v1/public/projects/" + project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Public Detail Project"))
                .andExpect(jsonPath("$.studentSlug").isNotEmpty());
    }

    @Test
    void getPublicProject_forPrivateProject_returns404() throws Exception {
        String token = registerWithPortfolio("project-private-detail@example.com");
        ProjectResponse project = createProject(token, "Private Detail Project", "PRIVATE");

        mockMvc.perform(get("/api/v1/public/projects/" + project.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProject_neverExposesEmail() throws Exception {
        String token = registerWithPortfolio("project-privacy-check@example.com");
        ProjectResponse project = createProject(token, "Privacy Check Project", "PUBLIC");

        String body = mockMvc.perform(get("/api/v1/public/projects/" + project.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("project-privacy-check@example.com");
        assertThat(body).doesNotContainIgnoringCase("\"email\"");
        assertThat(body).doesNotContainIgnoringCase("password");
    }
}
