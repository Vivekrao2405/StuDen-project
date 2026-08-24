package com.studen.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.questionbank.Difficulty;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.List;
import java.util.UUID;
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
class AdminResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String registerAdminAndGetToken(String email) throws Exception {
        String token = registerAndGetToken(email);
        userRepository.findByEmail(email).ifPresent(user -> user.setRole(UserRole.ADMIN));
        return token;
    }

    private UUID createSkill(String adminToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Resource Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private ResourceRequest externalLinkRequest(UUID skillId, String title, String... tags) {
        return new ResourceRequest(title, "A short description", ResourceType.EXTERNAL_LINK, skillId, Difficulty.EASY,
                15, "https://example.com/resource", null, List.of(tags));
    }

    private ResourceDetailResponse create(String adminToken, ResourceRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ResourceDetailResponse.class);
    }

    // --- Authorization -----------------------------------------------------------------------

    @Test
    void create_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("res-student-create@example.com");
        String adminToken = registerAdminAndGetToken("res-owner1@example.com");
        UUID skillId = createSkill(adminToken, "Res Auth Skill");

        mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(externalLinkRequest(skillId, "Blocked Resource"))))
                .andExpect(status().isForbidden());
    }

    // --- CRUD ---------------------------------------------------------------------------------

    @Test
    void create_thenGet_roundTripsAllFields() throws Exception {
        String adminToken = registerAdminAndGetToken("res-crud-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res CRUD Skill");

        ResourceDetailResponse created = create(adminToken,
                externalLinkRequest(skillId, "Intro to Res CRUD", "res-crud-tag-a", "res-crud-tag-b"));

        assertThat(created.title()).isEqualTo("Intro to Res CRUD");
        assertThat(created.resourceType()).isEqualTo(ResourceType.EXTERNAL_LINK);
        assertThat(created.skillId()).isEqualTo(skillId);
        assertThat(created.status()).isEqualTo(ResourceStatus.DRAFT);
        assertThat(created.tags()).containsExactlyInAnyOrder("res-crud-tag-a", "res-crud-tag-b");

        String body = mockMvc.perform(get("/api/v1/admin/resources/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ResourceDetailResponse fetched = objectMapper.readValue(body, ResourceDetailResponse.class);
        assertThat(fetched.id()).isEqualTo(created.id());
    }

    @Test
    void update_changesFields_evenAfterPublish() throws Exception {
        String adminToken = registerAdminAndGetToken("res-update-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res Update Skill");
        ResourceDetailResponse created = create(adminToken, externalLinkRequest(skillId, "Before Title", "res-update-tag"));

        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Unlike Question/PracticalAssessment, a Resource stays editable after PUBLISHED (spec
        // 7.7 doesn't call for immutable-once-published semantics for resources).
        String body = mockMvc.perform(put("/api/v1/admin/resources/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(externalLinkRequest(skillId, "After Title", "res-update-tag"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ResourceDetailResponse updated = objectMapper.readValue(body, ResourceDetailResponse.class);
        assertThat(updated.title()).isEqualTo("After Title");
        assertThat(updated.status()).isEqualTo(ResourceStatus.PUBLISHED);
    }

    // --- Publish validation per type ----------------------------------------------------------

    @Test
    void publish_pdfWithoutUploadedFile_returnsBadRequest() throws Exception {
        String adminToken = registerAdminAndGetToken("res-pdf-noupload-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res PDF Skill");
        ResourceRequest request = new ResourceRequest("Untouched PDF", "desc", ResourceType.PDF, skillId,
                Difficulty.EASY, 10, null, null, List.of("res-pdf-tag"));
        ResourceDetailResponse created = create(adminToken, request);

        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_notesWithoutContent_returnsBadRequest() throws Exception {
        String adminToken = registerAdminAndGetToken("res-notes-noupload-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res Notes Skill");
        ResourceRequest request = new ResourceRequest("Empty Notes", "desc", ResourceType.NOTES, skillId,
                Difficulty.EASY, 10, null, null, List.of("res-notes-tag"));
        ResourceDetailResponse created = create(adminToken, request);

        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_externalLinkWithUrl_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("res-link-publish-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res Link Publish Skill");
        ResourceDetailResponse created = create(adminToken, externalLinkRequest(skillId, "Publishable Link"));

        String body = mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(body, ResourceDetailResponse.class).status()).isEqualTo(ResourceStatus.PUBLISHED);
    }

    // --- Status transitions --------------------------------------------------------------------

    @Test
    void publish_thenUnpublish_returnsToDraft() throws Exception {
        String adminToken = registerAdminAndGetToken("res-unpub-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res Unpub Skill");
        ResourceDetailResponse created = create(adminToken, externalLinkRequest(skillId, "Unpublishable"));
        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/unpublish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(body, ResourceDetailResponse.class).status()).isEqualTo(ResourceStatus.DRAFT);
    }

    @Test
    void archive_twice_returnsConflictOnSecondCall() throws Exception {
        String adminToken = registerAdminAndGetToken("res-doublearchive-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res Double Archive Skill");
        ResourceDetailResponse created = create(adminToken, externalLinkRequest(skillId, "Archive Me"));

        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- Delete guard ---------------------------------------------------------------------------

    @Test
    void delete_untouchedDraft_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("res-delete-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res Delete Skill");
        ResourceDetailResponse created = create(adminToken, externalLinkRequest(skillId, "Delete Me"));

        mockMvc.perform(delete("/api/v1/admin/resources/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withExistingStudentProgress_returnsConflict() throws Exception {
        String adminToken = registerAdminAndGetToken("res-delete-blocked-admin@example.com");
        String studentToken = registerAndGetToken("res-delete-blocked-student@example.com");
        UUID skillId = createSkill(adminToken, "Res Delete Blocked Skill");
        ResourceDetailResponse created = create(adminToken, externalLinkRequest(skillId, "In Progress Resource"));
        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/resources/" + created.id() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/resources/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- List filters ----------------------------------------------------------------------------

    @Test
    void list_filtersByStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("res-list-admin@example.com");
        UUID skillId = createSkill(adminToken, "Res List Skill");
        ResourceDetailResponse draft = create(adminToken, externalLinkRequest(skillId, "Res List Draft"));
        ResourceDetailResponse published = create(adminToken, externalLinkRequest(skillId, "Res List Published"));
        mockMvc.perform(post("/api/v1/admin/resources/" + published.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("skillId", skillId.toString())
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ResourcePageResponse<ResourceSummaryResponse> page = objectMapper.readValue(body,
                objectMapper.getTypeFactory().constructParametricType(ResourcePageResponse.class, ResourceSummaryResponse.class));
        assertThat(page.content()).extracting(ResourceSummaryResponse::id).contains(published.id()).doesNotContain(draft.id());
    }
}
