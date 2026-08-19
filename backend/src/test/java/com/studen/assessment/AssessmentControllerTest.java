package com.studen.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
import com.studen.portfolio.PortfolioResponse;
import com.studen.portfolio.PortfolioSkillResponse;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionOptionRequest;
import com.studen.questionbank.QuestionRequest;
import com.studen.questionbank.QuestionResponse;
import com.studen.questionbank.QuestionType;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Covers spec §45's controller-level scenarios: published-only/difficulty-distribution selection,
 * no-leak-before-submit, IDOR on every lookup, answer validation, scoring per question type,
 * double-submission, and resume-vs-new-assessment. Every question pool used here is "uniform" (all
 * N published questions share the same option structure) so a specific assessment question can be
 * located reliably by option text regardless of which of the N underlying bank questions the
 * random selector actually picked — see {@link #createUniformSkill}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static QuestionOptionRequest opt(String text, int order, boolean correct) {
        return new QuestionOptionRequest(text, order, correct);
    }

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test Student", email, "SecurePassword123");
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

    private QuestionResponse publishQuestion(String adminToken, UUID skillId, Difficulty difficulty, QuestionType type,
            String text, List<QuestionOptionRequest> options) throws Exception {
        QuestionRequest request = new QuestionRequest(skillId, null, text, type, difficulty, "Explanation for: " + text,
                null, null, options);
        String createdBody = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(createdBody, QuestionResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String publishedBody = mockMvc.perform(post("/api/v1/admin/questions/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(publishedBody, QuestionResponse.class);
    }

    // All `count` questions share the same type/difficulty/options shape, so whichever subset the
    // random selector picks is behaviorally identical for the caller's assertions.
    private UUID createUniformSkill(String adminToken, String skillName, int count, Difficulty difficulty,
            QuestionType type, List<QuestionOptionRequest> options) throws Exception {
        UUID skillId = createSkill(adminToken, skillName);
        for (int i = 0; i < count; i++) {
            publishQuestion(adminToken, skillId, difficulty, type, skillName + " question " + i + "?", options);
        }
        return skillId;
    }

    // Assessment eligibility (see com.studen.portfolio.PortfolioSkillProfileService) requires the
    // skill to be on the student's portfolio before a *new* assessment can be started — every test
    // in this class funnels through this one helper, so this is the single place that needs to
    // account for it.
    private void ensurePortfolioSkill(String token, UUID skillId) throws Exception {
        var getResult = mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token)).andReturn();
        if (getResult.getResponse().getStatus() == 404) {
            PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null, Set.of(skillId), null);
            mockMvc.perform(post("/api/v1/portfolio")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
            return;
        }
        PortfolioResponse existing = objectMapper.readValue(getResult.getResponse().getContentAsString(), PortfolioResponse.class);
        if (existing.skills().stream().anyMatch(s -> s.id().equals(skillId))) {
            return;
        }
        Set<UUID> skillIds = new LinkedHashSet<>(existing.skills().stream().map(PortfolioSkillResponse::id).toList());
        skillIds.add(skillId);
        PortfolioRequest request = new PortfolioRequest(existing.headline(), existing.bio(), existing.experienceSummary(),
                existing.responseTime(), existing.location(), existing.available(), skillIds, existing.availableFor());
        mockMvc.perform(put("/api/v1/portfolio/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private AssessmentDetailResponse startAssessment(String token, UUID skillId) throws Exception {
        ensurePortfolioSkill(token, skillId);
        String body = mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(skillId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AssessmentDetailResponse.class);
    }

    private UUID optionIdByText(AssessmentQuestionView question, String text) {
        return question.options().stream()
                .filter(o -> o.optionText().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No option with text " + text))
                .id();
    }

    private AnswerResponse answer(String token, UUID assessmentId, UUID assessmentQuestionId, List<UUID> optionIds)
            throws Exception {
        var result = mockMvc.perform(patch("/api/v1/assessments/" + assessmentId + "/questions/" + assessmentQuestionId + "/answer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AnswerRequest(optionIds))));
        return objectMapper.readValue(result.andReturn().getResponse().getContentAsString(), AnswerResponse.class);
    }

    private AssessmentResultResponse submit(String token, UUID assessmentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AssessmentResultResponse.class);
    }

    // ---- Selection / generation ----

    @Test
    void start_generatesAssessmentWithConfiguredQuestionCount() throws Exception {
        String adminToken = registerAdminAndGetToken("as-count-admin@example.com");
        String studentToken = registerAndGetToken("as-count-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Count Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);

        assertThat(assessment.totalQuestions()).isEqualTo(20);
        assertThat(assessment.questions()).hasSize(20);
        assertThat(assessment.status()).isEqualTo(AssessmentStatus.IN_PROGRESS);
    }

    @Test
    void start_insufficientPublishedQuestions_returnsConflict() throws Exception {
        String adminToken = registerAdminAndGetToken("as-insufficient-admin@example.com");
        String studentToken = registerAndGetToken("as-insufficient-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Insufficient Skill", 12, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        ensurePortfolioSkill(studentToken, skillId);

        mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(skillId))))
                .andExpect(status().isConflict());
    }

    @Test
    void start_respectsConfiguredDifficultyDistribution() throws Exception {
        String adminToken = registerAdminAndGetToken("as-distribution-admin@example.com");
        String studentToken = registerAndGetToken("as-distribution-student@example.com");
        UUID skillId = createSkill(adminToken, "AS Distribution Skill");
        List<QuestionOptionRequest> options = List.of(opt("Option A", 0, true), opt("Option B", 1, false));
        for (int i = 0; i < 6; i++) publishQuestion(adminToken, skillId, Difficulty.EASY, QuestionType.MCQ_SINGLE, "Easy " + i + "?", options);
        for (int i = 0; i < 10; i++) publishQuestion(adminToken, skillId, Difficulty.MEDIUM, QuestionType.MCQ_SINGLE, "Medium " + i + "?", options);
        for (int i = 0; i < 4; i++) publishQuestion(adminToken, skillId, Difficulty.HARD, QuestionType.MCQ_SINGLE, "Hard " + i + "?", options);

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);

        Map<Difficulty, Long> counts = assessment.questions().stream()
                .collect(java.util.stream.Collectors.groupingBy(AssessmentQuestionView::difficulty, java.util.stream.Collectors.counting()));
        assertThat(counts.getOrDefault(Difficulty.EASY, 0L)).isEqualTo(6);
        assertThat(counts.getOrDefault(Difficulty.MEDIUM, 0L)).isEqualTo(10);
        assertThat(counts.getOrDefault(Difficulty.HARD, 0L)).isEqualTo(4);
    }

    @Test
    void start_draftReviewArchivedQuestions_neverSelected() throws Exception {
        String adminToken = registerAdminAndGetToken("as-status-admin@example.com");
        String studentToken = registerAndGetToken("as-status-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Status Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        // A DRAFT question (never submitted/published) and a since-ARCHIVED question — neither
        // should ever be selectable, so the pool below stays at exactly 20 PUBLISHED, not 22.
        QuestionRequest draftRequest = new QuestionRequest(skillId, null, "Still a draft?", QuestionType.MCQ_SINGLE,
                Difficulty.EASY, "why", null, null, List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftRequest)))
                .andExpect(status().isCreated());
        QuestionResponse toArchive = publishQuestion(adminToken, skillId, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                "Will be archived?", List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        mockMvc.perform(post("/api/v1/admin/questions/" + toArchive.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);

        assertThat(assessment.questions()).extracting(AssessmentQuestionView::questionText)
                .noneMatch(text -> text.equals("Still a draft?") || text.equals("Will be archived?"));
    }

    @Test
    void start_secondCall_resumesSameInProgressAssessment() throws Exception {
        String adminToken = registerAdminAndGetToken("as-resume-admin@example.com");
        String studentToken = registerAndGetToken("as-resume-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Resume Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));

        AssessmentDetailResponse first = startAssessment(studentToken, skillId);
        AssessmentDetailResponse second = startAssessment(studentToken, skillId);

        assertThat(second.id()).isEqualTo(first.id());
    }

    // ---- No leakage before submission ----

    @Test
    void get_beforeSubmission_rawJsonNeverLeaksAnswerKey() throws Exception {
        String adminToken = registerAdminAndGetToken("as-noleak-admin@example.com");
        String studentToken = registerAndGetToken("as-noleak-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS NoLeak Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse started = startAssessment(studentToken, skillId);

        String rawJson = mockMvc.perform(get("/api/v1/assessments/" + started.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(rawJson).doesNotContainIgnoringCase("isCorrect");
        assertThat(rawJson).doesNotContainIgnoringCase("explanation");
        assertThat(rawJson).doesNotContainIgnoringCase("correctOptionId");
    }

    // ---- IDOR ----

    @Test
    void get_asDifferentStudent_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("as-idor-get-admin@example.com");
        String studentA = registerAndGetToken("as-idor-get-a@example.com");
        String studentB = registerAndGetToken("as-idor-get-b@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS IDOR Get Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentA, skillId);

        mockMvc.perform(get("/api/v1/assessments/" + assessment.id())
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isNotFound());
    }

    @Test
    void answer_asDifferentStudent_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("as-idor-answer-admin@example.com");
        String studentA = registerAndGetToken("as-idor-answer-a@example.com");
        String studentB = registerAndGetToken("as-idor-answer-b@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS IDOR Answer Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentA, skillId);
        AssessmentQuestionView question = assessment.questions().get(0);
        UUID optionId = optionIdByText(question, "Option A");

        mockMvc.perform(patch("/api/v1/assessments/" + assessment.id() + "/questions/" + question.id() + "/answer")
                        .header("Authorization", "Bearer " + studentB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(optionId)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void submit_asDifferentStudent_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("as-idor-submit-admin@example.com");
        String studentA = registerAndGetToken("as-idor-submit-a@example.com");
        String studentB = registerAndGetToken("as-idor-submit-b@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS IDOR Submit Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentA, skillId);

        mockMvc.perform(post("/api/v1/assessments/" + assessment.id() + "/submit")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isNotFound());
    }

    // ---- Answer validation ----

    @Test
    void answer_unknownOptionId_rejected() throws Exception {
        String adminToken = registerAdminAndGetToken("as-invalidopt-admin@example.com");
        String studentToken = registerAndGetToken("as-invalidopt-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Invalid Option Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        UUID assessmentQuestionId = assessment.questions().get(0).id();

        mockMvc.perform(patch("/api/v1/assessments/" + assessment.id() + "/questions/" + assessmentQuestionId + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(UUID.randomUUID())))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answer_optionFromAnotherQuestion_rejected() throws Exception {
        String adminToken = registerAdminAndGetToken("as-crossq-admin@example.com");
        String studentToken = registerAndGetToken("as-crossq-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Cross Question Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        UUID firstQuestionId = assessment.questions().get(0).id();
        UUID optionFromSecondQuestion = assessment.questions().get(1).options().get(0).id();

        mockMvc.perform(patch("/api/v1/assessments/" + assessment.id() + "/questions/" + firstQuestionId + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(optionFromSecondQuestion)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answer_mcqSingle_withMultipleOptions_rejected() throws Exception {
        String adminToken = registerAdminAndGetToken("as-mcqsingle-multi-admin@example.com");
        String studentToken = registerAndGetToken("as-mcqsingle-multi-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS MCQ Single Multi Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        AssessmentQuestionView question = assessment.questions().get(0);
        List<UUID> bothOptions = question.options().stream().map(o -> o.id()).toList();

        mockMvc.perform(patch("/api/v1/assessments/" + assessment.id() + "/questions/" + question.id() + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(bothOptions))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answer_thenGet_persistsSelectionForResume() throws Exception {
        String adminToken = registerAdminAndGetToken("as-persist-admin@example.com");
        String studentToken = registerAndGetToken("as-persist-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Persist Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        AssessmentQuestionView question = assessment.questions().get(0);
        UUID optionId = optionIdByText(question, "Option A");

        answer(studentToken, assessment.id(), question.id(), List.of(optionId));

        AssessmentDetailResponse reloaded = objectMapper.readValue(
                mockMvc.perform(get("/api/v1/assessments/" + assessment.id())
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                AssessmentDetailResponse.class);

        AssessmentQuestionView reloadedQuestion = reloaded.questions().stream()
                .filter(q -> q.id().equals(question.id())).findFirst().orElseThrow();
        assertThat(reloadedQuestion.selectedOptionIds()).containsExactly(optionId);
    }

    // ---- Scoring ----

    @Test
    void submit_mcqSingle_correctAndIncorrectAnswersScoredIndependently() throws Exception {
        String adminToken = registerAdminAndGetToken("as-score-single-admin@example.com");
        String studentToken = registerAndGetToken("as-score-single-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Score Single Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        List<AssessmentQuestionView> questions = assessment.questions();

        // Answer half correctly, half incorrectly.
        for (int i = 0; i < questions.size(); i++) {
            AssessmentQuestionView q = questions.get(i);
            String text = i % 2 == 0 ? "Option A" : "Option B";
            answer(studentToken, assessment.id(), q.id(), List.of(optionIdByText(q, text)));
        }

        AssessmentResultResponse result = submit(studentToken, assessment.id());

        assertThat(result.correctCount()).isEqualTo(10);
        assertThat(result.scorePercentage()).isEqualTo(50);
        assertThat(result.status()).isEqualTo(AssessmentStatus.SUBMITTED);
    }

    @Test
    void submit_mcqMultiple_isAllOrNothing() throws Exception {
        String adminToken = registerAdminAndGetToken("as-score-multi-admin@example.com");
        String studentToken = registerAndGetToken("as-score-multi-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Score Multi Skill", 20, Difficulty.EASY, QuestionType.MCQ_MULTIPLE,
                List.of(opt("Correct A", 0, true), opt("Correct B", 1, true), opt("Wrong C", 2, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        List<AssessmentQuestionView> questions = assessment.questions();

        // q0: exactly the correct set -> correct. q1: partial (only one of two correct) -> wrong.
        // q2: correct set plus an extra wrong option -> wrong. Rest unanswered -> wrong.
        AssessmentQuestionView q0 = questions.get(0);
        answer(studentToken, assessment.id(), q0.id(),
                List.of(optionIdByText(q0, "Correct A"), optionIdByText(q0, "Correct B")));
        AssessmentQuestionView q1 = questions.get(1);
        answer(studentToken, assessment.id(), q1.id(), List.of(optionIdByText(q1, "Correct A")));
        AssessmentQuestionView q2 = questions.get(2);
        answer(studentToken, assessment.id(), q2.id(),
                List.of(optionIdByText(q2, "Correct A"), optionIdByText(q2, "Correct B"), optionIdByText(q2, "Wrong C")));

        AssessmentResultResponse result = submit(studentToken, assessment.id());

        assertThat(result.correctCount()).isEqualTo(1);
    }

    @Test
    void submit_trueFalse_scoresCorrectly() throws Exception {
        String adminToken = registerAdminAndGetToken("as-score-tf-admin@example.com");
        String studentToken = registerAndGetToken("as-score-tf-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Score TF Skill", 20, Difficulty.EASY, QuestionType.TRUE_FALSE,
                List.of(opt("True", 0, true), opt("False", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        AssessmentQuestionView q0 = assessment.questions().get(0);

        answer(studentToken, assessment.id(), q0.id(), List.of(optionIdByText(q0, "True")));

        AssessmentResultResponse result = submit(studentToken, assessment.id());

        AssessmentResultQuestionView reviewed = result.questions().stream().filter(q -> q.id().equals(q0.id())).findFirst().orElseThrow();
        assertThat(reviewed.correct()).isTrue();
    }

    @Test
    void submit_locksAssessment_furtherAnswersRejected() throws Exception {
        String adminToken = registerAdminAndGetToken("as-lock-admin@example.com");
        String studentToken = registerAndGetToken("as-lock-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Lock Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        submit(studentToken, assessment.id());

        AssessmentQuestionView question = assessment.questions().get(0);
        mockMvc.perform(patch("/api/v1/assessments/" + assessment.id() + "/questions/" + question.id() + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(optionIdByText(question, "Option A"))))))
                .andExpect(status().isConflict());
    }

    @Test
    void submit_twice_isIdempotentAndReturnsSameResult() throws Exception {
        String adminToken = registerAdminAndGetToken("as-doublesubmit-admin@example.com");
        String studentToken = registerAndGetToken("as-doublesubmit-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Double Submit Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);

        AssessmentResultResponse first = submit(studentToken, assessment.id());
        AssessmentResultResponse second = submit(studentToken, assessment.id());

        assertThat(second.scorePercentage()).isEqualTo(first.scorePercentage());
        assertThat(second.correctCount()).isEqualTo(first.correctCount());
        assertThat(second.status()).isEqualTo(AssessmentStatus.SUBMITTED);
    }

    @Test
    void get_afterSubmission_revealsCorrectAnswersAndExplanation() throws Exception {
        String adminToken = registerAdminAndGetToken("as-reveal-admin@example.com");
        String studentToken = registerAndGetToken("as-reveal-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Reveal Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        submit(studentToken, assessment.id());

        String rawJson = mockMvc.perform(get("/api/v1/assessments/" + assessment.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(rawJson).containsIgnoringCase("explanation");
        assertThat(rawJson).contains("correctOptionIds");
    }

    // ---- Skills listing ----

    @Test
    void listAssessableSkills_reflectsPublishedCountAndAssessableFlag() throws Exception {
        String adminToken = registerAdminAndGetToken("as-skills-admin@example.com");
        String studentToken = registerAndGetToken("as-skills-student@example.com");
        UUID readySkillId = createUniformSkill(adminToken, "AS Ready Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        UUID comingSoonSkillId = createUniformSkill(adminToken, "AS Coming Soon Skill", 5, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        // Eligibility (see com.studen.portfolio.PortfolioSkillProfileService): only skills on the
        // student's own portfolio are ever listed, regardless of what exists in the catalog.
        ensurePortfolioSkill(studentToken, readySkillId);
        ensurePortfolioSkill(studentToken, comingSoonSkillId);

        String body = mockMvc.perform(get("/api/v1/assessments/skills")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessableSkillsResponse response = objectMapper.readValue(body, AssessableSkillsResponse.class);
        assertThat(response.state()).isEqualTo(com.studen.portfolio.EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        List<AssessableSkillResponse> skills = response.skills();

        AssessableSkillResponse ready = skills.stream().filter(s -> s.skillId().equals(readySkillId)).findFirst().orElseThrow();
        AssessableSkillResponse comingSoon = skills.stream().filter(s -> s.skillId().equals(comingSoonSkillId)).findFirst().orElseThrow();
        assertThat(ready.assessable()).isTrue();
        assertThat(ready.publishedQuestionCount()).isEqualTo(20);
        assertThat(comingSoon.assessable()).isFalse();
        assertThat(comingSoon.publishedQuestionCount()).isEqualTo(5);
    }

    @Test
    void listAssessableSkills_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/assessments/skills")).andExpect(status().isUnauthorized());
    }

    // ---- Eligibility (Skill-visibility fix) ----

    @Test
    void listAssessableSkills_noPortfolio_returnsNoPortfolioState() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-noportfolio-admin@example.com");
        String studentToken = registerAndGetToken("as-elig-noportfolio-student@example.com");
        createUniformSkill(adminToken, "AS Elig No Portfolio Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));

        String body = mockMvc.perform(get("/api/v1/assessments/skills")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessableSkillsResponse response = objectMapper.readValue(body, AssessableSkillsResponse.class);

        assertThat(response.state()).isEqualTo(com.studen.portfolio.EligibilityState.NO_PORTFOLIO);
        assertThat(response.skills()).isEmpty();
    }

    @Test
    void listAssessableSkills_portfolioWithNoSkills_returnsNoSkillsState() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-noskills-admin@example.com");
        String studentToken = registerAndGetToken("as-elig-noskills-student@example.com");
        createUniformSkill(adminToken, "AS Elig No Skills Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        PortfolioRequest emptyPortfolio = new PortfolioRequest("Test Student", null, null, null, null, null, Set.of(), null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyPortfolio)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v1/assessments/skills")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessableSkillsResponse response = objectMapper.readValue(body, AssessableSkillsResponse.class);

        assertThat(response.state()).isEqualTo(com.studen.portfolio.EligibilityState.NO_SKILLS);
        assertThat(response.skills()).isEmpty();
    }

    @Test
    void listAssessableSkills_onlyReturnsSkillsOnStudentsPortfolio() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-scope-admin@example.com");
        String studentToken = registerAndGetToken("as-elig-scope-student@example.com");
        UUID javaSkillId = createUniformSkill(adminToken, "AS Elig Java Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        createUniformSkill(adminToken, "AS Elig Python Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        ensurePortfolioSkill(studentToken, javaSkillId); // Python deliberately not added

        String body = mockMvc.perform(get("/api/v1/assessments/skills")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessableSkillsResponse response = objectMapper.readValue(body, AssessableSkillsResponse.class);

        assertThat(response.skills()).extracting(AssessableSkillResponse::skillId).containsExactly(javaSkillId);
    }

    @Test
    void start_forSkillNotOnPortfolio_returnsForbidden() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-forbidden-admin@example.com");
        String studentToken = registerAndGetToken("as-elig-forbidden-student@example.com");
        UUID eligibleSkillId = createUniformSkill(adminToken, "AS Elig Forbidden Eligible Skill", 20, Difficulty.EASY,
                QuestionType.MCQ_SINGLE, List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        UUID ineligibleSkillId = createUniformSkill(adminToken, "AS Elig Forbidden Ineligible Skill", 20, Difficulty.EASY,
                QuestionType.MCQ_SINGLE, List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        ensurePortfolioSkill(studentToken, eligibleSkillId);

        mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(ineligibleSkillId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void start_forSkillWithNoPortfolioAtAll_returnsForbidden() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-nopf-admin@example.com");
        String studentToken = registerAndGetToken("as-elig-nopf-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Elig No Portfolio At All Skill", 20, Difficulty.EASY,
                QuestionType.MCQ_SINGLE, List.of(opt("Option A", 0, true), opt("Option B", 1, false)));

        mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(skillId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void start_afterSkillRemovedFromPortfolio_historicalAssessmentStillReadable() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-history-admin@example.com");
        String studentToken = registerAndGetToken("as-elig-history-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Elig History Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        submit(studentToken, assessment.id());

        // Remove the skill from the portfolio entirely.
        mockMvc.perform(put("/api/v1/portfolio/me")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PortfolioRequest("Test Student", null, null, null, null, null, Set.of(), null))))
                .andExpect(status().isOk());

        // The historical result must remain fully readable.
        mockMvc.perform(get("/api/v1/assessments/" + assessment.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // But starting a brand-new one for the now-removed skill is rejected.
        mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(skillId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAssessableSkills_studentACannotSeeStudentBsSkills() throws Exception {
        String adminToken = registerAdminAndGetToken("as-elig-idor-admin@example.com");
        String studentA = registerAndGetToken("as-elig-idor-a@example.com");
        String studentB = registerAndGetToken("as-elig-idor-b@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Elig IDOR Skill", 20, Difficulty.EASY, QuestionType.MCQ_SINGLE,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
        ensurePortfolioSkill(studentA, skillId); // only A has this skill

        String body = mockMvc.perform(get("/api/v1/assessments/skills")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessableSkillsResponse response = objectMapper.readValue(body, AssessableSkillsResponse.class);

        assertThat(response.state()).isEqualTo(com.studen.portfolio.EligibilityState.NO_PORTFOLIO);
        assertThat(response.skills()).isEmpty();
    }
}
