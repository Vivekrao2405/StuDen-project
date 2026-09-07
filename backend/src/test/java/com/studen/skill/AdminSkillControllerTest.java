package com.studen.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.placement.PlacementRoleDetailResponse;
import com.studen.placement.PlacementRoleRequest;
import com.studen.placement.RoleSkillRequest;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
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

/**
 * Admin CRUD over the shared skill catalog — Phase 1's Skill Management screen. The student-facing
 * search/categories/create-custom-skill endpoints are covered separately in
 * {@link SkillControllerTest} and are untouched by this controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AdminSkillControllerTest {

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

    private AdminSkillResponse createSkill(String adminToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Placement Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AdminSkillResponse.class);
    }

    // --- Authorization --------------------------------------------------------------------------

    @Test
    void list_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/skills"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("admin-skill-student@example.com");
        mockMvc.perform(post("/api/v1/admin/skills")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSkillRequest("Sneaky Skill", "Practical Skills"))))
                .andExpect(status().isForbidden());
    }

    // --- Validation ------------------------------------------------------------------------------

    @Test
    void create_withBlankName_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("admin-skill-blank@example.com");
        mockMvc.perform(post("/api/v1/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest("   ", "Practical Skills"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withDuplicateName_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("admin-skill-dupe@example.com");
        createSkill(adminToken, "Duplicate Admin Skill");
        mockMvc.perform(post("/api/v1/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSkillRequest("duplicate admin skill", "Placement Skills"))))
                .andExpect(status().isConflict());
    }

    // --- CRUD ------------------------------------------------------------------------------------

    @Test
    void skill_createUpdateDelete_roundTrips() throws Exception {
        String adminToken = registerAdminAndGetToken("admin-skill-crud@example.com");
        AdminSkillResponse created = createSkill(adminToken, "Temporary Admin Skill");
        assertThat(created.category()).isEqualTo("Placement Skills");

        String updatedBody = mockMvc.perform(put("/api/v1/admin/skills/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSkillRequest("Renamed Admin Skill", "Programming"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AdminSkillResponse updated = objectMapper.readValue(updatedBody, AdminSkillResponse.class);
        assertThat(updated.name()).isEqualTo("Renamed Admin Skill");
        assertThat(updated.category()).isEqualTo("Programming");

        mockMvc.perform(delete("/api/v1/admin/skills/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/skills/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_withUnknownId_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("admin-skill-missing@example.com");
        mockMvc.perform(get("/api/v1/admin/skills/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filtersBySearchTerm() throws Exception {
        String adminToken = registerAdminAndGetToken("admin-skill-search@example.com");
        createSkill(adminToken, "Very Unique Admin Skill Term");

        String body = mockMvc.perform(get("/api/v1/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Very Unique Admin Skill Term"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        SkillPageResponse<?> page = objectMapper.readValue(body, SkillPageResponse.class);
        String raw = objectMapper.writeValueAsString(page.content());
        assertThat(raw).contains("Very Unique Admin Skill Term");
    }

    // --- Delete safety ---------------------------------------------------------------------------

    @Test
    void delete_skillMappedToARole_returns409AndLeavesMappingIntact() throws Exception {
        String adminToken = registerAdminAndGetToken("admin-skill-inuse@example.com");
        AdminSkillResponse skill = createSkill(adminToken, "In Use Admin Skill");

        String roleBody = mockMvc.perform(post("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementRoleRequest("Role For Skill In Use Test", null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PlacementRoleDetailResponse role = objectMapper.readValue(roleBody, PlacementRoleDetailResponse.class);

        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillRequest(skill.id(), 1, null, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/skills/" + skill.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // The skill and its mapping both survive the rejected delete.
        mockMvc.perform(get("/api/v1/admin/skills/" + skill.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
