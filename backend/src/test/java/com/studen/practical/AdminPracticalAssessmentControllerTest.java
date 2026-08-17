package com.studen.practical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class AdminPracticalAssessmentControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private PracticalAssessmentRequest codingRequest(UUID skillId, String title, List<PracticalCodingLanguageRequest> languages,
            List<PracticalTestCaseRequest> testCases) {
        return new PracticalAssessmentRequest(title, skillId, PracticalType.CODING, WorkspaceType.CODE_EDITOR,
                Difficulty.MEDIUM, 30, "Given an array, find the longest consecutive sequence.", "N/A", "1 <= N <= 1000",
                EvaluationType.MANUAL, null, languages, testCases, null);
    }

    private List<PracticalCodingLanguageRequest> fourLanguages() {
        return List.of(
                new PracticalCodingLanguageRequest(CodingLanguage.JAVA, "public class Main {}"),
                new PracticalCodingLanguageRequest(CodingLanguage.PYTHON, "def main(): pass"),
                new PracticalCodingLanguageRequest(CodingLanguage.C, "int main() { return 0; }"),
                new PracticalCodingLanguageRequest(CodingLanguage.CPP, "int main() { return 0; }"));
    }

    private List<PracticalTestCaseRequest> oneVisibleOneHiddenTestCase() {
        return List.of(
                new PracticalTestCaseRequest("6\n100 4 200 1 3 2", "4", false, 0),
                new PracticalTestCaseRequest("3\n1 2 3", "3", true, 1));
    }

    private PracticalAssessmentDetailResponse create(String adminToken, PracticalAssessmentRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAssessmentDetailResponse.class);
    }

    private PracticalAssessmentDetailResponse publishFlow(String adminToken, UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAssessmentDetailResponse.class);
    }

    // --- Authorization ---------------------------------------------------------------------

    @Test
    void create_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("pa-student-create@example.com");
        String adminToken = registerAdminAndGetToken("pa-owner1@example.com");
        UUID skillId = createSkill(adminToken, "DSA Practical Guard");

        mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codingRequest(skillId, "Guarded", fourLanguages(), oneVisibleOneHiddenTestCase()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_asStudent_allReturn403() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-idor-admin@example.com");
        String studentToken = registerAndGetToken("pa-idor-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA IDOR Guard");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "IDOR Guard Problem", fourLanguages(), oneVisibleOneHiddenTestCase()));

        mockMvc.perform(get("/api/v1/admin/practical-assessments/" + created.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/practical-assessments/" + created.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // --- Create / publish validation --------------------------------------------------------

    @Test
    void create_asAdmin_startsAsDraft() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-create-admin@example.com");
        UUID skillId = createSkill(adminToken, "DSA Draft Skill");

        PracticalAssessmentDetailResponse response = create(adminToken,
                codingRequest(skillId, "Longest Consecutive Sequence", fourLanguages(), oneVisibleOneHiddenTestCase()));

        assertThat(response.status()).isEqualTo(PracticalAssessmentStatus.DRAFT);
        assertThat(response.languages()).hasSize(4);
        assertThat(response.testCases()).hasSize(2);
    }

    @Test
    void publish_withoutSubmitReview_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-publish-order@example.com");
        UUID skillId = createSkill(adminToken, "DSA Publish Order Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "Publish Order Problem", fourLanguages(), oneVisibleOneHiddenTestCase()));

        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void publish_codingWithoutLanguages_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-publish-no-lang@example.com");
        UUID skillId = createSkill(adminToken, "DSA No Lang Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "No Languages Problem", List.of(), oneVisibleOneHiddenTestCase()));

        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_codingWithoutTestCases_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-publish-no-tests@example.com");
        UUID skillId = createSkill(adminToken, "DSA No Tests Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "No Test Cases Problem", fourLanguages(), List.of()));

        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_rubricNotSummingTo100_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-rubric-invalid@example.com");
        UUID skillId = createSkill(adminToken, "UI/UX Rubric Invalid Skill");
        PracticalAssessmentRequest request = new PracticalAssessmentRequest("Bad Rubric Design Task", skillId,
                PracticalType.UI_UX, WorkspaceType.UI_UX_WORKSPACE, Difficulty.EASY, 45, "Design a login screen.", null,
                null, EvaluationType.MANUAL, null, null, null,
                List.of(new PracticalRubricCriterionRequest("Visual hierarchy", 20, 0),
                        new PracticalRubricCriterionRequest("Usability", 20, 1)));
        PracticalAssessmentDetailResponse created = create(adminToken, request);

        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_success_setsPublishedStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-publish-success@example.com");
        UUID skillId = createSkill(adminToken, "DSA Publish Success Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "Publish Success Problem", fourLanguages(), oneVisibleOneHiddenTestCase()));

        PracticalAssessmentDetailResponse published = publishFlow(adminToken, created.id());

        assertThat(published.status()).isEqualTo(PracticalAssessmentStatus.PUBLISHED);
    }

    // --- Versioning ---------------------------------------------------------------------------

    @Test
    void createNewVersion_ofPublished_forksDraftAndArchivesOldOnRepublish() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-version@example.com");
        UUID skillId = createSkill(adminToken, "DSA Version Skill");
        PracticalAssessmentDetailResponse v1 = create(adminToken,
                codingRequest(skillId, "Versioned Problem", fourLanguages(), oneVisibleOneHiddenTestCase()));
        publishFlow(adminToken, v1.id());

        String newVersionBody = mockMvc.perform(post("/api/v1/admin/practical-assessments/" + v1.id() + "/new-version")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PracticalAssessmentDetailResponse v2 = objectMapper.readValue(newVersionBody, PracticalAssessmentDetailResponse.class);
        assertThat(v2.status()).isEqualTo(PracticalAssessmentStatus.DRAFT);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.previousVersionId()).isEqualTo(v1.id());
        assertThat(v2.languages()).hasSize(4);
        assertThat(v2.testCases()).hasSize(2);

        publishFlow(adminToken, v2.id());

        String v1AfterBody = mockMvc.perform(get("/api/v1/admin/practical-assessments/" + v1.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(v1AfterBody, PracticalAssessmentDetailResponse.class).status())
                .isEqualTo(PracticalAssessmentStatus.ARCHIVED);
    }

    @Test
    void update_publishedAssessment_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-edit-published@example.com");
        UUID skillId = createSkill(adminToken, "DSA Edit Published Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "Immutable Once Published", fourLanguages(), oneVisibleOneHiddenTestCase()));
        publishFlow(adminToken, created.id());

        mockMvc.perform(put("/api/v1/admin/practical-assessments/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                codingRequest(skillId, "Sneaky Edit", fourLanguages(), oneVisibleOneHiddenTestCase()))))
                .andExpect(status().isConflict());
    }

    // --- Delete guard ---------------------------------------------------------------------------

    @Test
    void delete_untouchedDraft_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-delete-draft@example.com");
        UUID skillId = createSkill(adminToken, "DSA Delete Draft Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "Delete Me Draft", fourLanguages(), oneVisibleOneHiddenTestCase()));

        mockMvc.perform(delete("/api/v1/admin/practical-assessments/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_afterReview_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-delete-review@example.com");
        UUID skillId = createSkill(adminToken, "DSA Delete Review Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "Cannot Delete After Review", fourLanguages(), oneVisibleOneHiddenTestCase()));
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/practical-assessments/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void archive_alreadyArchived_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-archive-twice@example.com");
        UUID skillId = createSkill(adminToken, "DSA Archive Twice Skill");
        PracticalAssessmentDetailResponse created = create(adminToken,
                codingRequest(skillId, "Archive Twice Problem", fourLanguages(), oneVisibleOneHiddenTestCase()));
        publishFlow(adminToken, created.id());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + created.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- List / pagination --------------------------------------------------------------------

    @Test
    void list_filtersByPracticalTypeAndStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("pa-list-filter@example.com");
        UUID skillId = createSkill(adminToken, "DSA List Filter Skill");
        create(adminToken, codingRequest(skillId, "Filter Problem A", fourLanguages(), oneVisibleOneHiddenTestCase()));

        mockMvc.perform(get("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("practicalType", "CODING")
                        .param("status", "DRAFT")
                        .param("skillId", skillId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
