package com.studen.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.assessment.AssessmentLevel;
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
 * Roles, the Role -> Skill mapping, and companies: the catalog the rest of the placement system
 * reads from, plus the seed data that ships with the migration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementCatalogControllerTest {

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
                                new PlacementRoleRequest(name, "Role for tests", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementRoleDetailResponse.class);
    }

    private PlacementCompanyResponse createCompany(String adminToken, String name, CompanyType type) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementCompanyRequest(name, type, "A company", null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementCompanyResponse.class);
    }

    // --- Authorization ------------------------------------------------------------------------

    @Test
    void createRole_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/placement/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRole_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("pl-cat-student@example.com");
        mockMvc.perform(post("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementRoleRequest("Sneaky Role", null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRoles_asStudent_isAllowed() throws Exception {
        String studentToken = registerAndGetToken("pl-cat-student-read@example.com");
        mockMvc.perform(get("/api/v1/placement/roles")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    // --- Validation ---------------------------------------------------------------------------

    @Test
    void createRole_withBlankName_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-blank@example.com");
        mockMvc.perform(post("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementRoleRequest("   ", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRole_withDuplicateName_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-dupe@example.com");
        createRole(adminToken, "Duplicate Role Test");
        mockMvc.perform(post("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementRoleRequest("duplicate role test", null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void mapSkill_toUnknownRole_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-badrole@example.com");
        UUID skillId = createSkill(adminToken, "Placement Test Skill A");
        mockMvc.perform(post("/api/v1/admin/placement/roles/" + UUID.randomUUID() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillRequest(skillId, 3, null, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapUnknownSkill_toRole_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-badskill@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Role With Bad Skill");
        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RoleSkillRequest(UUID.randomUUID(), 3, null, 1))))
                .andExpect(status().isNotFound());
    }

    // --- Role CRUD ----------------------------------------------------------------------------

    @Test
    void role_createUpdateDelete_roundTrips() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-crud@example.com");
        PlacementRoleDetailResponse created = createRole(adminToken, "Temporary Role");
        assertThat(created.status()).isEqualTo(PlacementCatalogStatus.ACTIVE);
        assertThat(created.skills()).isEmpty();

        String updatedBody = mockMvc.perform(put("/api/v1/admin/placement/roles/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementRoleRequest("Renamed Role", "Updated description", 42))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementRoleDetailResponse updated =
                objectMapper.readValue(updatedBody, PlacementRoleDetailResponse.class);
        assertThat(updated.name()).isEqualTo("Renamed Role");
        assertThat(updated.displayOrder()).isEqualTo(42);

        mockMvc.perform(delete("/api/v1/admin/placement/roles/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/placement/roles/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatedRole_isHiddenFromStudentCatalogButVisibleToAdmin() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-deactivate@example.com");
        String studentToken = registerAndGetToken("pl-cat-deactivate-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Hidden Role");

        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String studentBody = mockMvc.perform(get("/api/v1/placement/roles")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<PlacementRoleResponse> studentRoles = List.of(
                objectMapper.readValue(studentBody, PlacementRoleResponse[].class));
        assertThat(studentRoles).noneMatch(r -> r.id().equals(role.id()));

        String adminBody = mockMvc.perform(get("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<PlacementRoleResponse> adminRoles = List.of(
                objectMapper.readValue(adminBody, PlacementRoleResponse[].class));
        assertThat(adminRoles).anyMatch(r -> r.id().equals(role.id()));
    }

    // --- Role -> Skill mapping ----------------------------------------------------------------

    @Test
    void roleSkills_replaceThenReadBack_carriesWeightProficiencyAndPriority() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-mapping@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Mapping Role");
        UUID sql = createSkill(adminToken, "Placement Mapping SQL");
        UUID python = createSkill(adminToken, "Placement Mapping Python");

        String body = mockMvc.perform(put("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillsRequest(List.of(
                                new RoleSkillRequest(sql, 5, AssessmentLevel.ADVANCED, 1),
                                new RoleSkillRequest(python, 3, AssessmentLevel.INTERMEDIATE, 2))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<RoleSkillResponse> mapped = List.of(objectMapper.readValue(body, RoleSkillResponse[].class));

        assertThat(mapped).hasSize(2);
        assertThat(mapped.get(0).skillId()).isEqualTo(sql);
        assertThat(mapped.get(0).weight()).isEqualTo(5);
        assertThat(mapped.get(0).requiredProficiency()).isEqualTo(AssessmentLevel.ADVANCED);
        assertThat(mapped.get(0).priority()).isEqualTo(1);
        assertThat(mapped.get(1).skillId()).isEqualTo(python);

        // The student-facing route answers the same question, which is what onboarding step 4 needs.
        String studentToken = registerAndGetToken("pl-cat-mapping-student@example.com");
        String studentBody = mockMvc.perform(get("/api/v1/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(List.of(objectMapper.readValue(studentBody, RoleSkillResponse[].class))).hasSize(2);
    }

    @Test
    void roleSkills_replaceWithSmallerSet_removesTheRest() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-shrink@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Shrinking Role");
        UUID keep = createSkill(adminToken, "Placement Shrink Keep");
        UUID drop = createSkill(adminToken, "Placement Shrink Drop");

        mockMvc.perform(put("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillsRequest(List.of(
                                new RoleSkillRequest(keep, 1, null, 1),
                                new RoleSkillRequest(drop, 1, null, 2))))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(put("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillsRequest(List.of(
                                new RoleSkillRequest(keep, 4, null, 1))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<RoleSkillResponse> mapped = List.of(objectMapper.readValue(body, RoleSkillResponse[].class));
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).skillId()).isEqualTo(keep);
        assertThat(mapped.get(0).weight()).isEqualTo(4);
    }

    @Test
    void roleSkills_addSameSkillTwice_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-dupe-skill@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Duplicate Skill Role");
        UUID skillId = createSkill(adminToken, "Placement Duplicate Skill");

        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillRequest(skillId, 1, null, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillRequest(skillId, 2, null, 2))))
                .andExpect(status().isConflict());
    }

    @Test
    void removeRoleSkill_leavesTheSkillItselfIntact() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-unmap@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Unmapping Role");
        UUID skillId = createSkill(adminToken, "Placement Unmap Skill");

        mockMvc.perform(post("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSkillRequest(skillId, 1, null, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/admin/placement/roles/" + role.id() + "/skills/" + skillId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(get("/api/v1/admin/placement/roles/" + role.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(List.of(objectMapper.readValue(body, RoleSkillResponse[].class))).isEmpty();

        // The shared catalog skill survives being unmapped.
        String search = mockMvc.perform(get("/api/v1/skills/search?q=Placement Unmap Skill")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(List.of(objectMapper.readValue(search, SkillResponse[].class)))
                .anyMatch(skill -> skill.id().equals(skillId));
    }

    // --- Companies ----------------------------------------------------------------------------

    @Test
    void company_createUpdateDelete_roundTrips() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-company@example.com");
        PlacementCompanyResponse created =
                createCompany(adminToken, "Placement Test Company", CompanyType.SERVICE_BASED);
        assertThat(created.companyType()).isEqualTo(CompanyType.SERVICE_BASED);
        assertThat(created.status()).isEqualTo(PlacementCatalogStatus.ACTIVE);

        String updatedBody = mockMvc.perform(put("/api/v1/admin/placement/companies/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementCompanyRequest(
                                "Placement Test Company", CompanyType.GCC, "Now a GCC", "https://example.com/l.png"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(updatedBody, PlacementCompanyResponse.class).companyType())
                .isEqualTo(CompanyType.GCC);

        mockMvc.perform(delete("/api/v1/admin/placement/companies/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void companies_studentSeesOnlyActiveOnes() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-company-status@example.com");
        String studentToken = registerAndGetToken("pl-cat-company-student@example.com");
        PlacementCompanyResponse active =
                createCompany(adminToken, "Placement Active Company", CompanyType.PRODUCT_BASED);
        PlacementCompanyResponse hidden =
                createCompany(adminToken, "Placement Hidden Company", CompanyType.STARTUP);

        mockMvc.perform(post("/api/v1/admin/placement/companies/" + hidden.id() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/placement/companies?search=Placement")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementPageResponse<?> page = objectMapper.readValue(body, PlacementPageResponse.class);
        String raw = objectMapper.writeValueAsString(page.content());
        assertThat(raw).contains(active.id().toString());
        assertThat(raw).doesNotContain(hidden.id().toString());
    }

    // --- Seed data ----------------------------------------------------------------------------

    @Test
    void seededRoles_existWithTheirSpecifiedSkillMappings() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-cat-seed@example.com");
        String body = mockMvc.perform(get("/api/v1/admin/placement/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<PlacementRoleResponse> roles = List.of(objectMapper.readValue(body, PlacementRoleResponse[].class));

        assertThat(roles).extracting(PlacementRoleResponse::name).contains(
                "Software Developer / SDE", "Data Analyst", "Data Scientist", "Frontend Developer",
                "Backend Developer", "Cloud / DevOps", "Cybersecurity");

        PlacementRoleResponse sde = roles.stream()
                .filter(role -> role.name().equals("Software Developer / SDE"))
                .findFirst().orElseThrow();
        String sdeSkills = mockMvc.perform(get("/api/v1/admin/placement/roles/" + sde.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(List.of(objectMapper.readValue(sdeSkills, RoleSkillResponse[].class)))
                .extracting(RoleSkillResponse::skillName)
                .containsExactlyInAnyOrder("Programming Fundamentals", "DSA", "OOP", "SQL", "DBMS",
                        "Operating Systems", "Computer Networks", "Aptitude");

        PlacementRoleResponse analyst = roles.stream()
                .filter(role -> role.name().equals("Data Analyst"))
                .findFirst().orElseThrow();
        String analystSkills = mockMvc.perform(get("/api/v1/admin/placement/roles/" + analyst.id() + "/skills")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(List.of(objectMapper.readValue(analystSkills, RoleSkillResponse[].class)))
                .extracting(RoleSkillResponse::skillName)
                .containsExactlyInAnyOrder("SQL", "Python", "Pandas", "NumPy", "Excel", "Statistics",
                        "Power BI", "Data Visualization", "Business Analysis");
    }
}
