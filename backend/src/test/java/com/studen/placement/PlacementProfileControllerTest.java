package com.studen.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
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

/**
 * PlacementProfile -> Role / CompanyTypes / Companies / Skills, including the upsert behaviour
 * that keeps a returning student from being onboarded twice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementProfileControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Placement Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private PlacementRoleDetailResponse createRole(String adminToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementRoleRequest(name, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementRoleDetailResponse.class);
    }

    private PlacementCompanyResponse createCompany(String adminToken, String name, CompanyType type) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementCompanyRequest(name, type, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementCompanyResponse.class);
    }

    private PlacementProfileResponse saveProfile(String token, PlacementProfileRequest request) throws Exception {
        String body = mockMvc.perform(put("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementProfileResponse.class);
    }

    @Test
    void getProfile_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/placement/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_beforeOnboarding_returns204() throws Exception {
        String studentToken = registerAndGetToken("pl-prof-empty@example.com");
        mockMvc.perform(get("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void saveProfile_withoutTargetRole_returns400() throws Exception {
        String studentToken = registerAndGetToken("pl-prof-norole@example.com");
        mockMvc.perform(put("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experienceLevel\":\"BEGINNER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveProfile_withUnknownRole_returns404() throws Exception {
        String studentToken = registerAndGetToken("pl-prof-badrole@example.com");
        mockMvc.perform(put("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementProfileRequest(
                                UUID.randomUUID(), ExperienceLevel.BEGINNER, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveProfile_withInactiveRole_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-inactive-admin@example.com");
        String studentToken = registerAndGetToken("pl-prof-inactive@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Retired Role");
        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementProfileRequest(
                                role.id(), ExperienceLevel.BEGINNER, null, null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveProfile_persistsRoleCompanyTypesCompaniesAndSkills() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-admin@example.com");
        String studentToken = registerAndGetToken("pl-prof-full@example.com");

        PlacementRoleDetailResponse role = createRole(adminToken, "Profile Target Role");
        PlacementCompanyResponse companyA = createCompany(adminToken, "Profile Company A", CompanyType.SERVICE_BASED);
        PlacementCompanyResponse companyB = createCompany(adminToken, "Profile Company B", CompanyType.PRODUCT_BASED);
        UUID skillA = createSkill(adminToken, "Profile Skill A");
        UUID skillB = createSkill(adminToken, "Profile Skill B");

        PlacementProfileResponse saved = saveProfile(studentToken, new PlacementProfileRequest(
                role.id(),
                ExperienceLevel.INTERMEDIATE,
                List.of(CompanyType.SERVICE_BASED, CompanyType.GCC),
                List.of(companyA.id(), companyB.id()),
                List.of("Profile Manual Startup"),
                List.of(skillA, skillB)));

        assertThat(saved.targetRoleId()).isEqualTo(role.id());
        assertThat(saved.experienceLevel()).isEqualTo(ExperienceLevel.INTERMEDIATE);
        assertThat(saved.companyTypes()).containsExactlyInAnyOrder(CompanyType.SERVICE_BASED, CompanyType.GCC);
        assertThat(saved.targetCompanies()).extracting(PlacementCompanyResponse::id)
                .containsExactlyInAnyOrder(companyA.id(), companyB.id());
        assertThat(saved.manualTargetCompanies()).containsExactly("Profile Manual Startup");
        assertThat(saved.currentSkills()).extracting(SkillResponse::id)
                .containsExactlyInAnyOrder(skillA, skillB);

        String fetched = mockMvc.perform(get("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(fetched, PlacementProfileResponse.class).id()).isEqualTo(saved.id());
    }

    @Test
    void saveProfile_twice_updatesTheSameProfileRatherThanCreatingASecond() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-upsert-admin@example.com");
        String studentToken = registerAndGetToken("pl-prof-upsert@example.com");
        PlacementRoleDetailResponse first = createRole(adminToken, "Upsert Role One");
        PlacementRoleDetailResponse second = createRole(adminToken, "Upsert Role Two");
        UUID skillId = createSkill(adminToken, "Upsert Skill");

        PlacementProfileResponse initial = saveProfile(studentToken, new PlacementProfileRequest(
                first.id(), ExperienceLevel.BEGINNER, List.of(CompanyType.STARTUP), null,
                List.of("Upsert Manual Co"), List.of(skillId)));
        assertThat(initial.manualTargetCompanies()).containsExactly("Upsert Manual Co");

        PlacementProfileResponse updated = saveProfile(studentToken, new PlacementProfileRequest(
                second.id(), ExperienceLevel.ADVANCED, List.of(CompanyType.CONSULTING), null, null, null));

        assertThat(updated.id()).isEqualTo(initial.id());
        assertThat(updated.targetRoleId()).isEqualTo(second.id());
        assertThat(updated.experienceLevel()).isEqualTo(ExperienceLevel.ADVANCED);
        assertThat(updated.companyTypes()).containsExactly(CompanyType.CONSULTING);
        assertThat(updated.currentSkills()).isEmpty();
        assertThat(updated.manualTargetCompanies()).isEmpty();
    }

    @Test
    void profiles_areScopedToTheirOwnStudent() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-scope-admin@example.com");
        String studentOne = registerAndGetToken("pl-prof-scope-one@example.com");
        String studentTwo = registerAndGetToken("pl-prof-scope-two@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Scoped Role");

        saveProfile(studentOne, new PlacementProfileRequest(
                role.id(), ExperienceLevel.BEGINNER, null, null, null, null));

        mockMvc.perform(get("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentTwo))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProfile_leavesRoleCompanyAndSkillsIntact() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-delete-admin@example.com");
        String studentToken = registerAndGetToken("pl-prof-delete@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Deletable Profile Role");
        PlacementCompanyResponse company = createCompany(adminToken, "Deletable Profile Company", CompanyType.OTHER);
        UUID skillId = createSkill(adminToken, "Deletable Profile Skill");

        saveProfile(studentToken, new PlacementProfileRequest(role.id(), ExperienceLevel.BEGINNER,
                List.of(CompanyType.OTHER), List.of(company.id()), null, List.of(skillId)));

        mockMvc.perform(delete("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/placement/roles/" + role.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/placement/companies/" + company.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void deletingARoleAStudentTargets_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-guard-admin@example.com");
        String studentToken = registerAndGetToken("pl-prof-guard@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Guarded Role");
        saveProfile(studentToken, new PlacementProfileRequest(
                role.id(), ExperienceLevel.BEGINNER, null, null, null, null));

        mockMvc.perform(delete("/api/v1/admin/placement/roles/" + role.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingACompanyAStudentTargets_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prof-guard-co-admin@example.com");
        String studentToken = registerAndGetToken("pl-prof-guard-co@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Guard Company Role");
        PlacementCompanyResponse company = createCompany(adminToken, "Guarded Company", CompanyType.CONSULTING);
        saveProfile(studentToken, new PlacementProfileRequest(
                role.id(), ExperienceLevel.BEGINNER, null, List.of(company.id()), null, null));

        mockMvc.perform(delete("/api/v1/admin/placement/companies/" + company.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
