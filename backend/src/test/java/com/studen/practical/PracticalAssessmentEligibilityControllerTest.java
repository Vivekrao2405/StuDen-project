package com.studen.practical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.EligibilityState;
import com.studen.portfolio.PortfolioRequest;
import com.studen.portfolio.PortfolioResponse;
import com.studen.questionbank.Difficulty;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.List;
import java.util.Set;
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
 * "Remove skill-assessment portfolio restriction" -- a student sees and can attempt every
 * PUBLISHED practical assessment, regardless of what (if anything) is on their portfolio. Mirrors
 * the equivalent knowledge-assessment coverage in {@code com.studen.assessment.AssessmentControllerTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000", "app.execution.enabled=false"})
class PracticalAssessmentEligibilityControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Eligibility Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private List<PracticalCodingLanguageRequest> oneLanguage() {
        return List.of(new PracticalCodingLanguageRequest(CodingLanguage.JAVA, "public class Main {}"));
    }

    private UUID publishAssessment(String adminToken, UUID skillId, String title) throws Exception {
        PracticalQuestionRequest question = new PracticalQuestionRequest(null, title, null, null, "Solve the problem.",
                null, null, null, 100, 0, oneLanguage(), List.of(new PracticalTestCaseRequest("1", "1", false, 0, null)), null);
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.CODING,
                WorkspaceType.CODE_EDITOR, Difficulty.MEDIUM, 30, "Complete this practical assessment.",
                EvaluationType.MANUAL, null, List.of(question));
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, PracticalAssessmentDetailResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        return id;
    }

    private void createPortfolio(String token, Set<UUID> skillIds) throws Exception {
        PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null, skillIds, null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void updatePortfolioSkills(String token, Set<UUID> skillIds) throws Exception {
        var getResult = mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token)).andReturn();
        PortfolioResponse existing = objectMapper.readValue(getResult.getResponse().getContentAsString(), PortfolioResponse.class);
        PortfolioRequest request = new PortfolioRequest(existing.headline(), existing.bio(), existing.experienceSummary(),
                existing.responseTime(), existing.location(), existing.available(), skillIds, existing.availableFor());
        mockMvc.perform(put("/api/v1/portfolio/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private PracticalAssessmentListResponse listAssessments(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/practical-assessments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAssessmentListResponse.class);
    }

    // Scoped to one skill via the listing's optional skillId filter — needed for assertions about
    // an *empty* result, since the shared dev database can carry other published assessments (for
    // unrelated skills) left over from other test classes/manual runs.
    private PracticalAssessmentListResponse listAssessmentsForSkill(String token, UUID skillId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/practical-assessments?skillId=" + skillId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAssessmentListResponse.class);
    }

    // ---- Listing: no portfolio restriction ----

    @Test
    void list_withoutAnyPortfolio_stillReturnsPublishedAssessment() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-noportfolio-admin@example.com");
        String studentToken = registerAndGetToken("pe-noportfolio-student@example.com");
        UUID skillId = createSkill(adminToken, "PE No Portfolio Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "No Portfolio Problem");

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.state()).isEqualTo(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id).contains(assessmentId);
    }

    @Test
    void list_withEmptyPortfolio_stillReturnsPublishedAssessment() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-noskills-admin@example.com");
        String studentToken = registerAndGetToken("pe-noskills-student@example.com");
        UUID skillId = createSkill(adminToken, "PE No Skills Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "No Skills Problem");
        createPortfolio(studentToken, Set.of());

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.state()).isEqualTo(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id).contains(assessmentId);
    }

    @Test
    void list_includesAssessmentsForSkillsNotOnPortfolio() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-java-admin@example.com");
        String studentToken = registerAndGetToken("pe-java-student@example.com");
        UUID javaSkillId = createSkill(adminToken, "PE Java Skill");
        UUID pythonSkillId = createSkill(adminToken, "PE Unrelated Python Skill");
        UUID javaAssessmentId = publishAssessment(adminToken, javaSkillId, "Java Problem");
        UUID pythonAssessmentId = publishAssessment(adminToken, pythonSkillId, "Python Problem");
        createPortfolio(studentToken, Set.of(javaSkillId)); // Python deliberately not on the portfolio

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id)
                .contains(javaAssessmentId, pythonAssessmentId);
    }

    @Test
    void list_withNoPublishedAssessmentsAtAll_returnsNoMatchingAssessmentsState() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-nomatch-admin@example.com");
        String studentToken = registerAndGetToken("pe-nomatch-student@example.com");
        UUID skillId = createSkill(adminToken, "PE No Match Skill"); // no assessment published for it

        PracticalAssessmentListResponse response = listAssessmentsForSkill(studentToken, skillId);

        assertThat(response.state()).isEqualTo(EligibilityState.NO_MATCHING_ASSESSMENTS);
        assertThat(response.page().content()).isEmpty();
    }

    @Test
    void list_studentBCanSeeStudentAsSkillAssessmentToo() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-shared-admin@example.com");
        String studentA = registerAndGetToken("pe-shared-a@example.com");
        String studentB = registerAndGetToken("pe-shared-b@example.com");
        UUID skillId = createSkill(adminToken, "PE Shared Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Shared Problem");
        createPortfolio(studentA, Set.of(skillId)); // only A has this skill on their portfolio

        PracticalAssessmentListResponse response = listAssessments(studentB);

        assertThat(response.state()).isEqualTo(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id).contains(assessmentId);
    }

    // ---- Direct access + attempt: no portfolio restriction ----

    @Test
    void get_forSkillNotOnPortfolio_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-getnoportfolio-admin@example.com");
        String studentToken = registerAndGetToken("pe-getnoportfolio-student@example.com");
        UUID otherSkillId = createSkill(adminToken, "PE Get No Portfolio Other Skill");
        UUID targetSkillId = createSkill(adminToken, "PE Get No Portfolio Target Skill");
        UUID targetAssessmentId = publishAssessment(adminToken, targetSkillId, "Target Problem");
        createPortfolio(studentToken, Set.of(otherSkillId));

        mockMvc.perform(get("/api/v1/practical-assessments/" + targetAssessmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void startAttempt_withNoPortfolioAtAll_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-startnoportfolio-admin@example.com");
        String studentToken = registerAndGetToken("pe-startnoportfolio-student@example.com");
        UUID skillId = createSkill(adminToken, "PE Start No Portfolio Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Start No Portfolio Problem");
        // Deliberately no portfolio at all.

        mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());
    }

    @Test
    void startAttempt_afterSkillRemoved_stillStartable_andHistoricalAttemptStillReadable() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-history-admin@example.com");
        String studentToken = registerAndGetToken("pe-history-student@example.com");
        UUID skillId = createSkill(adminToken, "PE History Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "History Problem");
        createPortfolio(studentToken, Set.of(skillId));

        String attemptBody = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResponse attempt = objectMapper.readValue(attemptBody, PracticalAttemptResponse.class);
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        updatePortfolioSkills(studentToken, Set.of()); // remove the skill entirely

        // Historical attempt remains fully readable.
        mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // A brand-new attempt for the now-removed skill still succeeds — no restriction.
        mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());
    }

    // ---- Category filter reuses the existing skill category field ----

    @Test
    void list_filtersByCategoryUsingExistingSkillCategoryField() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-category-admin@example.com");
        String studentToken = registerAndGetToken("pe-category-student@example.com");
        UUID skillId = createSkill(adminToken, "PE Category Skill"); // category "Eligibility Skills" (see createSkill)
        UUID assessmentId = publishAssessment(adminToken, skillId, "Category Problem");

        PracticalAssessmentListResponse response = listAssessments(studentToken);
        PracticalAssessmentSummaryResponse summary = response.page().content().stream()
                .filter(a -> a.id().equals(assessmentId)).findFirst().orElseThrow();

        assertThat(summary.category()).isEqualTo("Eligibility Skills");
    }
}
