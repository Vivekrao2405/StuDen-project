package com.studen.communication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.assessment.Assessment;
import com.studen.assessment.AssessmentRepository;
import com.studen.assessment.AssessmentStatus;
import com.studen.assessment.AssessmentType;
import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.time.Instant;
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
 * Verifies AudienceService/AudienceSpecificationBuilder against the three scenarios named
 * explicitly in the Communications Center spec: Portfolio=NOT EXISTS, Skill=Python AND
 * Assessment=NOT COMPLETED, and Skill=Java correctly excluding Python-only students. Portfolio/
 * skill setup goes through the real API (same pattern as PracticalAttemptControllerTest); knowledge
 * assessments are inserted directly via AssessmentRepository — this suite is testing audience
 * *filtering*, not the assessment-taking flow, which already has its own dedicated tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AudienceFilterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

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
                java.util.Set.of(skillId), null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void insertTerminalAssessment(String email, UUID skillId, int scorePercentage) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        Assessment assessment = new Assessment(user, skill, AssessmentType.KNOWLEDGE, 10, null);
        assessment.setStatus(AssessmentStatus.SUBMITTED);
        assessment.setCorrectCount(scorePercentage / 10);
        assessment.setScorePercentage(scorePercentage);
        assessment.setSubmittedAt(Instant.now());
        assessmentRepository.save(assessment);
    }

    private String preview(String adminToken, String filterJson) throws Exception {
        return preview(adminToken, filterJson, false);
    }

    private String preview(String adminToken, String filterJson, boolean marketing) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/communications/campaigns/audience-preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AudiencePreviewRequest(filterJson, marketing))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // --- Scenario 1: Portfolio = NOT EXISTS -----------------------------------------------------

    @Test
    void portfolioNotExists_matchesOnlyStudentsWithoutAPortfolio() throws Exception {
        String adminToken = registerAdminAndGetToken("af-p1-admin@example.com");
        registerAndGetToken("af-p1-noportfolio@example.com");
        String withPortfolioToken = registerAndGetToken("af-p1-hasportfolio@example.com");
        UUID skillId = createSkill(adminToken, "AF Portfolio Skill");
        createPortfolioWithSkill(withPortfolioToken, skillId);

        String filter = """
                {"field":"PORTFOLIO_NOT_EXISTS","params":{}}""";

        // Admin (has no portfolio either) + af-p1-noportfolio both match; af-p1-hasportfolio must not.
        String body = preview(adminToken, filter);
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("af-p1-hasportfolio");
        AudiencePreviewResponse response = objectMapper.readValue(body, AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(response.sampleFirstNames()).isNotEmpty();
    }

    // --- Scenario 2: Skill = Python AND Assessment = NOT COMPLETED -------------------------------

    @Test
    void skillPythonAndAssessmentNotCompleted_excludesStudentsWhoCompletedIt() throws Exception {
        String adminToken = registerAdminAndGetToken("af-p2-admin@example.com");
        UUID pythonId = createSkill(adminToken, "AF Python");

        String neverAttempted = "af-p2-never-attempted@example.com";
        String completedIt = "af-p2-completed@example.com";
        String tokenA = registerAndGetToken(neverAttempted);
        String tokenB = registerAndGetToken(completedIt);
        createPortfolioWithSkill(tokenA, pythonId);
        createPortfolioWithSkill(tokenB, pythonId);
        insertTerminalAssessment(completedIt, pythonId, 80);

        String filter = objectMapper.writeValueAsString(java.util.Map.of(
                "operator", "AND",
                "children", java.util.List.of(
                        java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", pythonId.toString())),
                        java.util.Map.of("field", "ASSESSMENT_NOT_COMPLETED", "params",
                                java.util.Map.of("skillId", pythonId.toString())))));

        String body = preview(adminToken, filter);
        AudiencePreviewResponse response = objectMapper.readValue(body, AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(response.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(neverAttempted).isNotBlank();
    }

    // --- Scenario 3: Skill = Java correctly excludes Python-only students ------------------------

    @Test
    void skillJava_excludesPythonOnlyStudents() throws Exception {
        String adminToken = registerAdminAndGetToken("af-p3-admin@example.com");
        UUID javaId = createSkill(adminToken, "AF Java");
        UUID pythonId = createSkill(adminToken, "AF Python Only");

        String javaStudent = "af-p3-java@example.com";
        String pythonStudent = "af-p3-python@example.com";
        createPortfolioWithSkill(registerAndGetToken(javaStudent), javaId);
        createPortfolioWithSkill(registerAndGetToken(pythonStudent), pythonId);

        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", javaId.toString())));

        String body = preview(adminToken, filter);
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(pythonStudent);
        AudiencePreviewResponse response = objectMapper.readValue(body, AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(response.count()).isEqualTo(1);
    }

    // --- AND vs OR group semantics ----------------------------------------------------------------

    @Test
    void orGroup_matchesEitherBranch_andGroup_requiresBoth() throws Exception {
        String adminToken = registerAdminAndGetToken("af-orand-admin@example.com");
        UUID skillA = createSkill(adminToken, "AF OrAnd Skill A");
        UUID skillB = createSkill(adminToken, "AF OrAnd Skill B");

        String hasOnlyA = "af-orand-a@example.com";
        String hasOnlyB = "af-orand-b@example.com";
        String hasNeither = "af-orand-neither@example.com";
        createPortfolioWithSkill(registerAndGetToken(hasOnlyA), skillA);
        createPortfolioWithSkill(registerAndGetToken(hasOnlyB), skillB);
        registerAndGetToken(hasNeither);

        String orFilter = objectMapper.writeValueAsString(java.util.Map.of(
                "operator", "OR",
                "children", java.util.List.of(
                        java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillA.toString())),
                        java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillB.toString())))));
        AudiencePreviewResponse orResponse = objectMapper.readValue(preview(adminToken, orFilter),
                AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(orResponse.count()).isEqualTo(2);

        String andFilter = objectMapper.writeValueAsString(java.util.Map.of(
                "operator", "AND",
                "children", java.util.List.of(
                        java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillA.toString())),
                        java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillB.toString())))));
        AudiencePreviewResponse andResponse = objectMapper.readValue(preview(adminToken, andFilter),
                AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(andResponse.count()).isZero();
    }

    @Test
    void deletedUser_neverMatchesAnyFilter() throws Exception {
        String adminToken = registerAdminAndGetToken("af-deleted-admin@example.com");
        String targetEmail = "af-deleted-target@example.com";
        registerAndGetToken(targetEmail);
        User target = userRepository.findByEmail(targetEmail).orElseThrow();
        target.setDeletedAt(Instant.now());
        target.setActive(false);
        userRepository.save(target);

        String filter = """
                {"field":"USER_INACTIVE","params":{}}""";
        String body = preview(adminToken, filter);
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("af-deleted-target");
    }

    // Locks in the fix for the estimate/actual-send divergence: audience-preview must apply the
    // identical marketing-opt-out exclusion CampaignSendService.resolveAndQueue applies for a real
    // send, driven by the SAME AudienceSpecificationBuilder.build(node, marketing) path — never a
    // count that overstates who a marketing campaign will actually reach.
    @Test
    void previewAudience_marketingTrue_excludesOptedOutUser_marketingFalse_includesThem() throws Exception {
        String adminToken = registerAdminAndGetToken("af-optout-admin@example.com");
        UUID skillId = createSkill(adminToken, "AF OptOut Skill");
        String optedOutEmail = "af-optout-user@example.com";
        createPortfolioWithSkill(registerAndGetToken(optedOutEmail), skillId);
        User optedOut = userRepository.findByEmail(optedOutEmail).orElseThrow();
        optedOut.setMarketingOptOut(true);
        userRepository.save(optedOut);

        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillId.toString())));

        AudiencePreviewResponse marketingPreview = objectMapper
                .readValue(preview(adminToken, filter, true), AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(marketingPreview.count()).isZero();

        AudiencePreviewResponse transactionalPreview = objectMapper
                .readValue(preview(adminToken, filter, false), AudiencePreviewResponse.class);
        org.assertj.core.api.Assertions.assertThat(transactionalPreview.count()).isEqualTo(1);
    }

    @Test
    void previewAudience_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("af-forbidden-student@example.com");
        mockMvc.perform(post("/api/v1/admin/communications/campaigns/audience-preview")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AudiencePreviewRequest("{}", false))))
                .andExpect(status().isForbidden());
    }
}
