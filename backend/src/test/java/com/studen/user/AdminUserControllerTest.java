package com.studen.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.LoginRequest;
import com.studen.auth.RegisterRequest;
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
// See AuthControllerTest for why: AuthRateLimitFilter's per-IP counter is shared (and
// accumulates) across every @SpringBootTest in this JVM run.
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AdminUserControllerTest {

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

    /** Registers a fresh user and promotes them to ADMIN directly via the repository — this
     * codebase has no in-app promotion endpoint by design (see AdminBootstrapRunner), so tests
     * simulate the env-var bootstrap by flipping the role the same way. Role is re-read from the
     * DB on every request (UserDetailsServiceImpl), so no new token is needed after promoting. */
    private String registerAdminAndGetToken(String email) throws Exception {
        String token = registerAndGetToken(email);
        userRepository.findByEmail(email).ifPresent(user -> user.setRole(UserRole.ADMIN));
        return token;
    }

    private UUID userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // --- Authorization matrix (spec §32.2/§32.4/§32.11/§32.14) --------------------------------

    @Test
    void list_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allAdminUserMutations_asStudent_return403() throws Exception {
        String adminToken = registerAdminAndGetToken("au-idor-admin@example.com");
        String studentToken = registerAndGetToken("au-idor-student@example.com");
        String otherStudentToken = registerAndGetToken("au-idor-other@example.com");
        UUID otherId = userId("au-idor-other@example.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users/" + otherId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/" + otherId + "/deactivate")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/" + otherId + "/restore")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/users/" + otherId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isForbidden());

        // A student attempting to directly target another student's id via the (blocked) admin
        // API is still just a 403 — IDOR is prevented by the role check, not by ownership logic.
        assertThat(otherStudentToken).isNotBlank();
        assertThat(adminToken).isNotBlank();
    }

    // --- List / search / pagination (spec §32.1) -----------------------------------------------

    @Test
    void list_asAdmin_searchMatchesNameOrEmail() throws Exception {
        String adminToken = registerAdminAndGetToken("au-search-admin@example.com");
        registerAndGetToken("au-search-zebra-target@example.com");
        registerAndGetToken("au-search-unrelated@example.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "zebra-target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("au-search-zebra-target@example.com"));
    }

    @Test
    void list_asAdmin_neverExposesPasswordHash() throws Exception {
        String adminToken = registerAdminAndGetToken("au-noexpose-admin@example.com");
        registerAndGetToken("au-noexpose-target@example.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "noexpose-target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    // --- View detail (spec §32.3) ---------------------------------------------------------------

    @Test
    void get_asAdmin_returnsDetailWithDerivedActiveStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("au-detail-admin@example.com");
        registerAndGetToken("au-detail-target@example.com");
        UUID targetId = userId("au-detail-target@example.com");

        mockMvc.perform(get("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value("au-detail-target@example.com"));
    }

    @Test
    void get_unknownUser_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("au-404-admin@example.com");

        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- Deactivate / restore (spec §32.5-9) -----------------------------------------------------

    @Test
    void deactivate_asAdmin_blocksLoginAndRestoreReenablesIt() throws Exception {
        String adminToken = registerAdminAndGetToken("au-deactivate-admin@example.com");
        registerAndGetToken("au-deactivate-target@example.com");
        UUID targetId = userId("au-deactivate-target@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        LoginRequest loginRequest = new LoginRequest("au-deactivate-target@example.com", "SecurePassword123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivate_alreadyDeactivatedUser_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("au-double-deactivate-admin@example.com");
        registerAndGetToken("au-double-deactivate-target@example.com");
        UUID targetId = userId("au-double-deactivate-target@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivate_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("au-deactivate-student@example.com");
        registerAndGetToken("au-deactivate-victim@example.com");
        UUID targetId = userId("au-deactivate-victim@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/deactivate")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // --- Self / admin protection (spec §32.12-13) -------------------------------------------------

    @Test
    void deactivate_self_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("au-self-deactivate@example.com");
        UUID adminId = userId("au-self-deactivate@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + adminId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_self_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("au-self-delete@example.com");
        UUID adminId = userId("au-self-delete@example.com");

        mockMvc.perform(delete("/api/v1/admin/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot delete your own admin account."));
    }

    @Test
    void delete_anotherAdmin_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("au-delete-admin-actor@example.com");
        String otherAdminToken = registerAdminAndGetToken("au-delete-admin-target@example.com");
        UUID targetAdminId = userId("au-delete-admin-target@example.com");
        assertThat(otherAdminToken).isNotBlank();

        mockMvc.perform(delete("/api/v1/admin/users/" + targetAdminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin accounts cannot be deleted from User Management."));
    }

    // --- Permanent delete (spec §32.10, §32.15-16, §32.25) -----------------------------------------

    @Test
    void delete_withInvalidConfirmation_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("au-bad-confirm-admin@example.com");
        registerAndGetToken("au-bad-confirm-target@example.com");
        UUID targetId = userId("au-bad-confirm-target@example.com");

        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"delete"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_asAdmin_anonymizesAccountAndBlocksLogin() throws Exception {
        String adminToken = registerAdminAndGetToken("au-delete-admin@example.com");
        registerAndGetToken("au-delete-target@example.com");
        UUID targetId = userId("au-delete-target@example.com");

        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.fullName").value("Deleted User"))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.not("au-delete-target@example.com")));

        User target = userRepository.findById(targetId).orElseThrow();
        assertThat(target.isActive()).isFalse();
        assertThat(target.getDeletedAt()).isNotNull();

        LoginRequest loginRequest = new LoginRequest("au-delete-target@example.com", "SecurePassword123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_repeatedRequest_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("au-double-delete-admin@example.com");
        registerAndGetToken("au-double-delete-target@example.com");
        UUID targetId = userId("au-double-delete-target@example.com");

        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void restore_deletedUser_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("au-restore-deleted-admin@example.com");
        registerAndGetToken("au-restore-deleted-target@example.com");
        UUID targetId = userId("au-restore-deleted-target@example.com");

        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmation":"DELETE"}"""))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
