package com.studen.practical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// app.execution.enabled=false is explicit and load-bearing here, not incidental: this class
// predates the Phase 7.5 live sandbox (see ExecutionSandboxIntegrationTest for that) and
// run_dockerUnreachable_... below asserts the graceful UnavailableCodeExecutionService fallback
// specifically -- it must not start passing/failing depending on whether a real Docker Engine
// happens to be reachable on whatever machine runs this suite.
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000", "app.execution.enabled=false"})
class PracticalAttemptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticalAssessmentRepository practicalAssessmentRepository;

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

    private List<PracticalCodingLanguageRequest> fourLanguages() {
        return List.of(
                new PracticalCodingLanguageRequest(CodingLanguage.JAVA, "public class Main {}"),
                new PracticalCodingLanguageRequest(CodingLanguage.PYTHON, "def main(): pass"),
                new PracticalCodingLanguageRequest(CodingLanguage.C, "int main() { return 0; }"),
                new PracticalCodingLanguageRequest(CodingLanguage.CPP, "int main() { return 0; }"));
    }

    private UUID publishCodingAssessment(String adminToken, UUID skillId, String title, String hiddenSecretMarker) throws Exception {
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.CODING,
                WorkspaceType.CODE_EDITOR, Difficulty.MEDIUM, 30, "Find the longest consecutive sequence.", null, null,
                EvaluationType.MANUAL, null, fourLanguages(),
                List.of(new PracticalTestCaseRequest("6\n100 4 200 1 3 2", "4", false, 0, null),
                        new PracticalTestCaseRequest("HIDDEN_INPUT_" + hiddenSecretMarker, "HIDDEN_OUTPUT_" + hiddenSecretMarker, true, 1, null)),
                null);
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, PracticalAssessmentDetailResponse.class).id();

        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return id;
    }

    private UUID publishRubricUiUxAssessment(String adminToken, UUID skillId, String title) throws Exception {
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.UI_UX,
                WorkspaceType.UI_UX_WORKSPACE, Difficulty.EASY, 45, "Design a mobile login screen.", "Responsive, clean",
                null, EvaluationType.MANUAL, null, null, null,
                List.of(new PracticalRubricCriterionRequest("Visual hierarchy", 50, 0),
                        new PracticalRubricCriterionRequest("Usability", 50, 1)));
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, PracticalAssessmentDetailResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return id;
    }

    // Eligibility (see com.studen.portfolio.PortfolioSkillProfileService) requires the assessment's
    // skill to be on the student's portfolio before a *new* attempt can be started. Idempotent
    // (safe to call more than once for the same student, e.g. the resume/autosave tests below).
    private void ensurePortfolioSkill(String token, UUID skillId) throws Exception {
        int status = mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        if (status == 404) {
            PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null, Set.of(skillId), null);
            mockMvc.perform(post("/api/v1/portfolio")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    private UUID resolveSkillId(UUID assessmentId) {
        return practicalAssessmentRepository.findById(assessmentId).orElseThrow(AssertionError::new).getSkill().getId();
    }

    private PracticalAttemptResponse startAttempt(String studentToken, UUID assessmentId) throws Exception {
        ensurePortfolioSkill(studentToken, resolveSkillId(assessmentId));
        String body = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAttemptResponse.class);
    }

    // --- Visibility ------------------------------------------------------------------------

    @Test
    void list_draftAssessment_notVisibleToStudent() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-draft-admin@example.com");
        String studentToken = registerAndGetToken("pat-draft-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Draft Visibility Skill");
        PracticalAssessmentRequest request = new PracticalAssessmentRequest("Draft Only Problem", skillId,
                PracticalType.CODING, WorkspaceType.CODE_EDITOR, Difficulty.EASY, 30, "instructions", null, null,
                EvaluationType.MANUAL, null, fourLanguages(),
                List.of(new PracticalTestCaseRequest("1", "1", false, 0, null)), null);
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, PracticalAssessmentDetailResponse.class).id();

        mockMvc.perform(get("/api/v1/practical-assessments/" + id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        assertThat(studentToken).isNotBlank();
    }

    // Pre-start: the actual problem (public test cases included, not just hidden ones) must never
    // appear anywhere in this response — raw-string assertions on top of the typed check, since a
    // field merely being absent from the DTO's Java type wouldn't catch a stray leak via some other
    // serialized property.
    @Test
    void get_beforeStart_returnsMetadataOnly_neverLeaksProblemContent() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-hidden-admin@example.com");
        String studentToken = registerAndGetToken("pat-hidden-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Hidden Leak Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Hidden Leak Problem", "SECRET42");
        ensurePortfolioSkill(studentToken, skillId);

        String body = mockMvc.perform(get("/api/v1/practical-assessments/" + assessmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("HIDDEN_INPUT_SECRET42")
                .doesNotContain("HIDDEN_OUTPUT_SECRET42")
                .doesNotContain("100 4 200 1 3 2")
                .doesNotContain("public class Main {}")
                .doesNotContain("def main(): pass")
                .doesNotContain("\"publicTestCases\"")
                .doesNotContain("\"requirements\"")
                .doesNotContain("\"constraints\"")
                .doesNotContain("\"configurationJson\"")
                .doesNotContain("\"languages\"")
                .doesNotContain("starterCode");

        StudentPracticalAssessmentResponse response = objectMapper.readValue(body, StudentPracticalAssessmentResponse.class);
        assertThat(response.title()).isEqualTo("Hidden Leak Problem");
        assertThat(response.supportedLanguages()).containsExactlyInAnyOrder(
                CodingLanguage.JAVA, CodingLanguage.PYTHON, CodingLanguage.C, CodingLanguage.CPP);
    }

    // --- Attempt lifecycle -------------------------------------------------------------------

    // The flip side of the test above: once a real attempt exists, the full problem — public test
    // case (never the hidden one), starter code per language, instructions — must be present.
    @Test
    void startAttempt_returnsFullProblemContent_includingPublicTestCaseButNeverHidden() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-startcontent-admin@example.com");
        String studentToken = registerAndGetToken("pat-startcontent-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Start Content Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Start Content Problem", "SC1");
        ensurePortfolioSkill(studentToken, skillId);

        String body = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("HIDDEN_INPUT_SC1").doesNotContain("HIDDEN_OUTPUT_SC1");
        PracticalAttemptResponse attempt = objectMapper.readValue(body, PracticalAttemptResponse.class);
        assertThat(attempt.publicTestCases()).hasSize(1);
        assertThat(attempt.publicTestCases().get(0).expectedOutput()).isEqualTo("4");
        assertThat(attempt.languages()).hasSize(4);
        assertThat(attempt.languages()).anySatisfy(l -> assertThat(l.starterCode()).isEqualTo("public class Main {}"));
        assertThat(attempt.instructions()).isEqualTo("Find the longest consecutive sequence.");
    }

    // Resuming/refreshing mid-attempt (GET /practical-attempts/{id} while IN_PROGRESS) must keep
    // returning the full content too — the workspace re-fetches this on every page load/refresh.
    @Test
    void getAttempt_whileInProgress_alsoReturnsFullProblemContent() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-resumecontent-admin@example.com");
        String studentToken = registerAndGetToken("pat-resumecontent-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Resume Content Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Resume Content Problem", "RC1");
        PracticalAttemptResponse started = startAttempt(studentToken, assessmentId);

        String body = mockMvc.perform(get("/api/v1/practical-attempts/" + started.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResponse fetched = objectMapper.readValue(body, PracticalAttemptResponse.class);
        assertThat(fetched.publicTestCases()).hasSize(1);
        assertThat(fetched.languages()).hasSize(4);
    }

    @Test
    void startAttempt_resumesExistingInProgressAttempt() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-resume-admin@example.com");
        String studentToken = registerAndGetToken("pat-resume-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Resume Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Resume Problem", "R1");

        PracticalAttemptResponse first = startAttempt(studentToken, assessmentId);
        PracticalAttemptResponse second = startAttempt(studentToken, assessmentId);

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void attempt_differentStudent_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-idor-admin@example.com");
        String ownerToken = registerAndGetToken("pat-idor-owner@example.com");
        String intruderToken = registerAndGetToken("pat-idor-intruder@example.com");
        UUID skillId = createSkill(adminToken, "DSA IDOR Attempt Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "IDOR Attempt Problem", "I1");

        PracticalAttemptResponse attempt = startAttempt(ownerToken, assessmentId);

        mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest("hacked", CodingLanguage.PYTHON, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveProgress_autosaves_withoutCreatingNewAttempt() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-autosave-admin@example.com");
        String studentToken = registerAndGetToken("pat-autosave-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Autosave Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Autosave Problem", "A1");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        String saveBody = mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest("print('hi')", CodingLanguage.PYTHON, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResponse saved = objectMapper.readValue(saveBody, PracticalAttemptResponse.class);
        assertThat(saved.id()).isEqualTo(attempt.id());
        assertThat(saved.submissionContent()).isEqualTo("print('hi')");
        assertThat(saved.selectedLanguage()).isEqualTo(CodingLanguage.PYTHON);

        // Starting again resumes the SAME attempt, confirming no duplicate row was created.
        PracticalAttemptResponse resumed = startAttempt(studentToken, assessmentId);
        assertThat(resumed.id()).isEqualTo(attempt.id());
        assertThat(resumed.submissionContent()).isEqualTo("print('hi')");
    }

    @Test
    void submit_transitionsToUnderReview_andIsIdempotentOnSecondCall() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-submit-admin@example.com");
        String studentToken = registerAndGetToken("pat-submit-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Submit Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Submit Problem", "S1");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        String firstBody = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResultResponse first = objectMapper.readValue(firstBody, PracticalAttemptResultResponse.class);
        assertThat(first.status()).isEqualTo(PracticalAttemptStatus.UNDER_REVIEW);

        String secondBody = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResultResponse second = objectMapper.readValue(secondBody, PracticalAttemptResultResponse.class);
        assertThat(second.status()).isEqualTo(PracticalAttemptStatus.UNDER_REVIEW);
        assertThat(second.submittedAt()).isEqualTo(first.submittedAt());
    }

    @Test
    void editingAfterSubmit_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-edit-after-submit-admin@example.com");
        String studentToken = registerAndGetToken("pat-edit-after-submit-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Edit After Submit Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Edit After Submit Problem", "E1");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest("late edit", null, null))))
                .andExpect(status().isConflict());
    }

    // This test suite runs without a Docker Engine reachable, so DockerCodeExecutionService's own
    // isAvailable() check fails and ExecutionOrchestrator reports SYSTEM_ERROR -- exactly the real
    // "execution infrastructure unreachable" path (spec: never treat this as a student-code
    // failure). Real compile/run behavior against an actual sandbox is covered separately by tests
    // gated on Docker being reachable.
    @Test
    void run_dockerUnreachable_returnsSafeSystemErrorMessage_neverFakeResults_andNeverEditsSubmissionOnFailure() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-run-admin@example.com");
        String studentToken = registerAndGetToken("pat-run-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Run Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Run Problem", "RUN1");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SaveAttemptRequest("public class Main { public static void main(String[] a) {} }",
                                        CodingLanguage.JAVA, null))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/run")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("temporarily unavailable")))
                .andExpect(jsonPath("$.testsPassed").doesNotExist())
                .andExpect(jsonPath("$.publicTestResults").isEmpty());

        // A run-history row was still recorded (SYSTEM_ERROR is a real, visible event) and the
        // attempt itself was never advanced/finalized by an infra failure.
        String historyBody = mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id() + "/executions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(historyBody).contains("SYSTEM_ERROR");

        String stillInProgressBody = mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResponse stillInProgress = objectMapper.readValue(stillInProgressBody, PracticalAttemptResponse.class);
        assertThat(stillInProgress.status()).isEqualTo(PracticalAttemptStatus.IN_PROGRESS);
    }

    // --- Admin evaluation --------------------------------------------------------------------

    @Test
    void adminEvaluate_studentCannotAccess_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-eval-guard-admin@example.com");
        String studentToken = registerAndGetToken("pat-eval-guard-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Eval Guard Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Eval Guard Problem", "EG1");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        mockMvc.perform(get("/api/v1/admin/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/practical-attempts/" + attempt.id() + "/evaluate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluateAttemptRequest(null, 80, "Good"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEvaluate_rubricBased_computesScoreServerSide_andNotifiesStudent_andFeedsEvidence() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-rubric-eval-admin@example.com");
        String studentToken = registerAndGetToken("pat-rubric-eval-student@example.com");
        UUID skillId = createSkill(adminToken, "UI/UX Rubric Eval Skill");
        UUID assessmentId = publishRubricUiUxAssessment(adminToken, skillId, "Rubric Eval Design Task");
        ensurePortfolioSkill(studentToken, skillId);

        String startBody = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResponse attempt = objectMapper.readValue(startBody, PracticalAttemptResponse.class);

        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest(null, null, "https://figma.com/my-design"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        String detailBody = mockMvc.perform(get("/api/v1/admin/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AdminPracticalAttemptDetailResponse detail = objectMapper.readValue(detailBody, AdminPracticalAttemptDetailResponse.class);
        assertThat(detail.rubricCriteria()).hasSize(2);
        UUID criterion1 = detail.rubricCriteria().get(0).id();
        UUID criterion2 = detail.rubricCriteria().get(1).id();

        EvaluateAttemptRequest evaluateRequest = new EvaluateAttemptRequest(
                List.of(new EvaluateAttemptRequest.RubricScoreEntry(criterion1, 40),
                        new EvaluateAttemptRequest.RubricScoreEntry(criterion2, 35)),
                999, // deliberately wrong/ignored — server must compute 40+35=75, not trust this
                "Solid layout, needs contrast work.");
        String evalBody = mockMvc.perform(post("/api/v1/admin/practical-attempts/" + attempt.id() + "/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evaluateRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AdminPracticalAttemptDetailResponse evaluated = objectMapper.readValue(evalBody, AdminPracticalAttemptDetailResponse.class);
        assertThat(evaluated.status()).isEqualTo(PracticalAttemptStatus.EVALUATED);
        assertThat(evaluated.score()).isEqualTo(75);

        // Student result reflects the server-computed score.
        String resultBody = mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResultResponse result = objectMapper.readValue(resultBody, PracticalAttemptResultResponse.class);
        assertThat(result.score()).isEqualTo(75);

        // Practical evidence for the skill now reflects this evaluated attempt.
        String evidenceBody = mockMvc.perform(get("/api/v1/skills/" + skillId + "/practical-evidence")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalEvidenceResponse evidence = objectMapper.readValue(evidenceBody, PracticalEvidenceResponse.class);
        assertThat(evidence.score()).isEqualTo(75);
        assertThat(evidence.practicalAssessmentId()).isEqualTo(assessmentId);
    }

    @Test
    void evaluate_alreadyEvaluated_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pat-double-eval-admin@example.com");
        String studentToken = registerAndGetToken("pat-double-eval-student@example.com");
        UUID skillId = createSkill(adminToken, "DSA Double Eval Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Double Eval Problem", "DE1");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        EvaluateAttemptRequest evaluateRequest = new EvaluateAttemptRequest(null, 90, "Nice work");
        mockMvc.perform(post("/api/v1/admin/practical-attempts/" + attempt.id() + "/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evaluateRequest)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-attempts/" + attempt.id() + "/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evaluateRequest)))
                .andExpect(status().isConflict());
    }
}
