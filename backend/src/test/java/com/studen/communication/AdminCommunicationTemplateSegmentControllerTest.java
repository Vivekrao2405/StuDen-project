package com.studen.communication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CommunicationTestSupport.class)
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000",
        "app.communication.async.enabled=false"})
class AdminCommunicationTemplateSegmentControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Communications Test"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private void createPortfolioWithSkill(String token, UUID skillId) throws Exception {
        PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null,
                Set.of(skillId), null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    // --- Templates ---------------------------------------------------------------------------

    @Test
    void templateCrud_createUpdateDuplicateArchive() throws Exception {
        String adminToken = registerAdminAndGetToken("tpl-admin@example.com");
        TemplateRequest create = new TemplateRequest("Welcome Template", CommunicationCategory.SYSTEM_ANNOUNCEMENT,
                "Welcome!", "<p>Hi {{firstName}}</p>", "Push title", "Push body", "In-app title", "In-app body",
                "Open", "https://studen.app");

        String body = mockMvc.perform(post("/api/v1/admin/communications/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, TemplateResponse.class).id();

        mockMvc.perform(get("/api/v1/admin/communications/templates")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        TemplateRequest updated = new TemplateRequest("Welcome Template v2", CommunicationCategory.SYSTEM_ANNOUNCEMENT,
                "Welcome v2!", "<p>Updated</p>", null, null, null, null, null, null);
        mockMvc.perform(patch("/api/v1/admin/communications/templates/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Welcome Template v2"));

        String dupBody = mockMvc.perform(post("/api/v1/admin/communications/templates/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Welcome Template v2 (copy)"))
                .andReturn().getResponse().getContentAsString();
        UUID dupId = objectMapper.readValue(dupBody, TemplateResponse.class).id();

        mockMvc.perform(post("/api/v1/admin/communications/templates/" + id + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/communications/templates")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id=='" + dupId + "')]").exists());
    }

    @Test
    void templates_asStudent_return403() throws Exception {
        String studentToken = registerAndGetToken("tpl-student@example.com");
        mockMvc.perform(get("/api/v1/admin/communications/templates")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // --- Segments: live re-resolution -------------------------------------------------------------

    @Test
    void segment_previewReResolvesLive_asUnderlyingDataChanges() throws Exception {
        String adminToken = registerAdminAndGetToken("seg-admin@example.com");
        UUID skillId = createSkill(adminToken, "Segment Skill");
        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillId.toString())));

        String body = mockMvc.perform(post("/api/v1/admin/communications/segments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SegmentRequest("Has Skill", "desc", filter))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID segmentId = objectMapper.readValue(body, SegmentResponse.class).id();

        mockMvc.perform(post("/api/v1/admin/communications/segments/" + segmentId + "/preview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // Segment stores only the filter definition — adding a matching student changes the live
        // count automatically, with no edit to the segment itself.
        createPortfolioWithSkill(registerAndGetToken("seg-newmatch@example.com"), skillId);

        mockMvc.perform(post("/api/v1/admin/communications/segments/" + segmentId + "/preview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(delete("/api/v1/admin/communications/segments/" + segmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
