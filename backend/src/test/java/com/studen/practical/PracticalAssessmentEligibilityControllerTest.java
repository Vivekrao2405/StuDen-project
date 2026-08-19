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
 * "Fix assessment eligibility based on portfolio skills" -- a student only ever sees/starts
 * practical assessments for skills actually on their portfolio (see
 * {@code com.studen.portfolio.PortfolioSkillProfileService}). Mirrors the equivalent knowledge-
 * assessment coverage in {@code com.studen.assessment.AssessmentControllerTest}.
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
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.CODING,
                WorkspaceType.CODE_EDITOR, Difficulty.MEDIUM, 30, "Solve the problem.", null, null,
                EvaluationType.MANUAL, null, oneLanguage(), List.of(new PracticalTestCaseRequest("1", "1", false, 0, null)),
                null);
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

    // ---- Listing eligibility ----

    @Test
    void list_noPortfolio_returnsNoPortfolioStateAndNoAssessments() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-noportfolio-admin@example.com");
        String studentToken = registerAndGetToken("pe-noportfolio-student@example.com");
        UUID skillId = createSkill(adminToken, "PE No Portfolio Skill");
        publishAssessment(adminToken, skillId, "No Portfolio Problem");

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.state()).isEqualTo(EligibilityState.NO_PORTFOLIO);
        assertThat(response.page().content()).isEmpty();
    }

    @Test
    void list_portfolioWithNoSkills_returnsNoSkillsState() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-noskills-admin@example.com");
        String studentToken = registerAndGetToken("pe-noskills-student@example.com");
        UUID skillId = createSkill(adminToken, "PE No Skills Skill");
        publishAssessment(adminToken, skillId, "No Skills Problem");
        createPortfolio(studentToken, Set.of());

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.state()).isEqualTo(EligibilityState.NO_SKILLS);
        assertThat(response.page().content()).isEmpty();
    }

    @Test
    void list_skillOnPortfolio_assessmentBecomesVisible() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-python-admin@example.com");
        String studentToken = registerAndGetToken("pe-python-student@example.com");
        UUID pythonSkillId = createSkill(adminToken, "PE Python Skill");
        UUID assessmentId = publishAssessment(adminToken, pythonSkillId, "Python Problem");
        createPortfolio(studentToken, Set.of(pythonSkillId));

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.state()).isEqualTo(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id).containsExactly(assessmentId);
    }

    @Test
    void list_javaSkillDoesNotMatchUnrelatedPythonAssessment() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-java-admin@example.com");
        String studentToken = registerAndGetToken("pe-java-student@example.com");
        UUID javaSkillId = createSkill(adminToken, "PE Java Skill");
        UUID pythonSkillId = createSkill(adminToken, "PE Unrelated Python Skill");
        UUID javaAssessmentId = publishAssessment(adminToken, javaSkillId, "Java Problem");
        publishAssessment(adminToken, pythonSkillId, "Python Problem");
        createPortfolio(studentToken, Set.of(javaSkillId));

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id).containsExactly(javaAssessmentId);
    }

    @Test
    void list_multipleSkills_allMatchingAssessmentsVisible() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-multi-admin@example.com");
        String studentToken = registerAndGetToken("pe-multi-student@example.com");
        UUID reactSkillId = createSkill(adminToken, "PE React Skill");
        UUID nodeSkillId = createSkill(adminToken, "PE Node Skill");
        UUID reactAssessmentId = publishAssessment(adminToken, reactSkillId, "React Problem");
        UUID nodeAssessmentId = publishAssessment(adminToken, nodeSkillId, "Node Problem");
        createPortfolio(studentToken, Set.of(reactSkillId, nodeSkillId));

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.page().content()).extracting(PracticalAssessmentSummaryResponse::id)
                .containsExactlyInAnyOrder(reactAssessmentId, nodeAssessmentId);
    }

    @Test
    void list_skillsWithNoPublishedAssessment_returnsNoMatchingAssessmentsState() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-nomatch-admin@example.com");
        String studentToken = registerAndGetToken("pe-nomatch-student@example.com");
        UUID skillId = createSkill(adminToken, "PE No Match Skill"); // no assessment published for it
        createPortfolio(studentToken, Set.of(skillId));

        PracticalAssessmentListResponse response = listAssessments(studentToken);

        assertThat(response.state()).isEqualTo(EligibilityState.NO_MATCHING_ASSESSMENTS);
        assertThat(response.page().content()).isEmpty();
    }

    @Test
    void list_studentACannotSeeStudentBsSkills() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-idor-admin@example.com");
        String studentA = registerAndGetToken("pe-idor-a@example.com");
        String studentB = registerAndGetToken("pe-idor-b@example.com");
        UUID skillId = createSkill(adminToken, "PE IDOR Skill");
        publishAssessment(adminToken, skillId, "IDOR Problem");
        createPortfolio(studentA, Set.of(skillId)); // only A has this skill

        PracticalAssessmentListResponse response = listAssessments(studentB);

        assertThat(response.state()).isEqualTo(EligibilityState.NO_PORTFOLIO);
        assertThat(response.page().content()).isEmpty();
    }

    // ---- Direct access protection ----

    @Test
    void get_forSkillNotOnPortfolio_returnsForbidden() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-getforbidden-admin@example.com");
        String studentToken = registerAndGetToken("pe-getforbidden-student@example.com");
        UUID eligibleSkillId = createSkill(adminToken, "PE Get Forbidden Eligible Skill");
        UUID ineligibleSkillId = createSkill(adminToken, "PE Get Forbidden Ineligible Skill");
        UUID ineligibleAssessmentId = publishAssessment(adminToken, ineligibleSkillId, "Ineligible Problem");
        createPortfolio(studentToken, Set.of(eligibleSkillId));

        mockMvc.perform(get("/api/v1/practical-assessments/" + ineligibleAssessmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void startAttempt_forSkillNotOnPortfolio_returnsForbidden() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-startforbidden-admin@example.com");
        String studentToken = registerAndGetToken("pe-startforbidden-student@example.com");
        UUID skillId = createSkill(adminToken, "PE Start Forbidden Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Start Forbidden Problem");
        // Deliberately no portfolio at all.

        mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void startAttempt_afterSkillRemoved_newAttemptForbidden_butHistoricalAttemptStillReadable() throws Exception {
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

        // But a brand-new attempt for the now-removed skill is rejected.
        mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // ---- Portfolio-update responsiveness ----

    @Test
    void list_addingSkillToPortfolio_makesAssessmentAvailable() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-add-admin@example.com");
        String studentToken = registerAndGetToken("pe-add-student@example.com");
        UUID javaSkillId = createSkill(adminToken, "PE Add Java Skill");
        UUID pythonSkillId = createSkill(adminToken, "PE Add Python Skill");
        UUID pythonAssessmentId = publishAssessment(adminToken, pythonSkillId, "Add Python Problem");
        createPortfolio(studentToken, Set.of(javaSkillId));

        assertThat(listAssessments(studentToken).page().content())
                .extracting(PracticalAssessmentSummaryResponse::id).doesNotContain(pythonAssessmentId);

        updatePortfolioSkills(studentToken, Set.of(javaSkillId, pythonSkillId));

        assertThat(listAssessments(studentToken).page().content())
                .extracting(PracticalAssessmentSummaryResponse::id).contains(pythonAssessmentId);
    }

    @Test
    void list_removingSkillFromPortfolio_removesAssessmentFromEligibleList() throws Exception {
        String adminToken = registerAdminAndGetToken("pe-remove-admin@example.com");
        String studentToken = registerAndGetToken("pe-remove-student@example.com");
        UUID javaSkillId = createSkill(adminToken, "PE Remove Java Skill");
        UUID sqlSkillId = createSkill(adminToken, "PE Remove Sql Skill");
        UUID javaAssessmentId = publishAssessment(adminToken, javaSkillId, "Remove Java Problem");
        createPortfolio(studentToken, Set.of(javaSkillId, sqlSkillId));

        assertThat(listAssessments(studentToken).page().content())
                .extracting(PracticalAssessmentSummaryResponse::id).contains(javaAssessmentId);

        updatePortfolioSkills(studentToken, Set.of(sqlSkillId)); // Java removed

        assertThat(listAssessments(studentToken).page().content())
                .extracting(PracticalAssessmentSummaryResponse::id).doesNotContain(javaAssessmentId);
    }
}
