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
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionOptionRequest;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionRequest;
import com.studen.questionbank.QuestionResponse;
import com.studen.questionbank.QuestionType;
import com.studen.questionbank.Topic;
import com.studen.questionbank.TopicRepository;
import com.studen.questionbank.TopicRequest;
import com.studen.questionbank.TopicResponse;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.LinkedHashSet;
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
 * Phase 7.3: skill level mapping, topic performance, strong/weak buckets, historical integrity,
 * multiple attempts/latest-result, and result-endpoint security. Reuses the same
 * register/skill/question/start/answer/submit helper pattern as {@link AssessmentControllerTest}
 * and {@link AssessmentExpiryControllerTest} (each controller test file is self-contained).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class SkillResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionRepository questionRepository;

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

    private UUID createTopic(String adminToken, UUID skillId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TopicRequest(skillId, name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TopicResponse.class).id();
    }

    private UUID publishQuestion(String adminToken, UUID skillId, UUID topicId, String text) throws Exception {
        QuestionRequest request = new QuestionRequest(skillId, topicId, text, QuestionType.MCQ_SINGLE, Difficulty.EASY,
                "Explanation for: " + text, null, null, List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
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
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return id;
    }

    // Publishes `count` uniform (same option shape) questions under one skill/topic — the specific
    // question picked out of the pool never matters for these tests since every question in it is
    // behaviorally identical (same idiom as AssessmentControllerTest.createUniformSkill).
    private List<UUID> publishUniformQuestions(String adminToken, UUID skillId, UUID topicId, String prefix, int count)
            throws Exception {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(publishQuestion(adminToken, skillId, topicId, prefix + " question " + i + "?"));
        }
        return ids;
    }

    // Tag-wise analysis fix: same shape as publishQuestion, but with a tag instead of/alongside a
    // topic (topicId is always null here — these tests care specifically about tag-based grouping,
    // never mixing in the topic snapshot unless a test explicitly wants that combination).
    private UUID publishQuestionWithTags(String adminToken, UUID skillId, String text, String tag) throws Exception {
        QuestionRequest request = new QuestionRequest(skillId, null, text, QuestionType.MCQ_SINGLE, Difficulty.EASY,
                "Explanation for: " + text, null, tag,
                List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
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
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return id;
    }

    private List<UUID> publishQuestionsWithTag(String adminToken, UUID skillId, String prefix, int count, String tag)
            throws Exception {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(publishQuestionWithTags(adminToken, skillId, prefix + " question " + i + "?", tag));
        }
        return ids;
    }

    // Eligibility (see com.studen.portfolio.PortfolioSkillProfileService) requires the skill to be
    // on the student's portfolio before a *new* assessment can be started. Idempotent (safe to call
    // more than once for the same student/skill, e.g. the retake test below).
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

    private void answer(String token, UUID assessmentId, UUID assessmentQuestionId, UUID optionId) throws Exception {
        mockMvc.perform(patch("/api/v1/assessments/" + assessmentId + "/questions/" + assessmentQuestionId + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(optionId)))))
                .andExpect(status().isOk());
    }

    // Answers the first `correctCount` questions (by display order) with the correct option and the
    // rest with the wrong one — gives exact, deterministic control over the resulting score.
    private void answerExactlyNCorrect(String token, AssessmentDetailResponse assessment, int correctCount) throws Exception {
        List<AssessmentQuestionView> questions = assessment.questions();
        for (int i = 0; i < questions.size(); i++) {
            AssessmentQuestionView q = questions.get(i);
            String text = i < correctCount ? "Option A" : "Option B";
            answer(token, assessment.id(), q.id(), optionIdByText(q, text));
        }
    }

    private AssessmentResultResponse submit(String token, UUID assessmentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AssessmentResultResponse.class);
    }

    private AssessmentResultSummaryResponse getResult(String token, UUID assessmentId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/assessments/" + assessmentId + "/result")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AssessmentResultSummaryResponse.class);
    }

    // ---- Level mapping (spec §25 items 1-6) ----

    @Test
    void level_zeroPercent_isBeginner() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl0-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl0-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 0 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 0);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.scorePercentage()).isEqualTo(0);
        assertThat(result.level()).isEqualTo(AssessmentLevel.BEGINNER);
        assertThat(result.correctCount()).isEqualTo(0);
        assertThat(result.incorrectCount()).isEqualTo(30);
    }

    @Test
    void level_20Percent_isBeginner() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl20-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl20-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 20 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 6);
        submit(studentToken, assessment.id());

        assertThat(getResult(studentToken, assessment.id()).level()).isEqualTo(AssessmentLevel.BEGINNER);
    }

    @Test
    void level_exactly40Percent_isDeveloping() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl40-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl40-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 40 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 12);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());
        assertThat(result.scorePercentage()).isEqualTo(40);
        assertThat(result.level()).isEqualTo(AssessmentLevel.DEVELOPING);
    }

    @Test
    void level_exactly60Percent_isIntermediate() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl60-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl60-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 60 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 18);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());
        assertThat(result.scorePercentage()).isEqualTo(60);
        assertThat(result.level()).isEqualTo(AssessmentLevel.INTERMEDIATE);
    }

    @Test
    void level_exactly80Percent_isAdvanced() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl80-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl80-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 80 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 24);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());
        assertThat(result.scorePercentage()).isEqualTo(80);
        assertThat(result.level()).isEqualTo(AssessmentLevel.ADVANCED);
    }

    @Test
    void level_exactly90Percent_isExpert() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl90-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl90-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 90 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 27);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());
        assertThat(result.scorePercentage()).isEqualTo(90);
        assertThat(result.level()).isEqualTo(AssessmentLevel.EXPERT);
    }

    @Test
    void level_100Percent_isExpert() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-lvl100-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl100-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 100 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 30);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());
        assertThat(result.scorePercentage()).isEqualTo(100);
        assertThat(result.level()).isEqualTo(AssessmentLevel.EXPERT);
        assertThat(result.correctCount()).isEqualTo(30);
        assertThat(result.incorrectCount()).isEqualTo(0);
    }

    @Test
    void level_79Percent_isIntermediateNotAdvanced() throws Exception {
        // Just below the ADVANCED boundary — proves the threshold check is >= not >.
        String adminToken = registerAdminAndGetToken("sr-lvl79-admin@example.com");
        String studentToken = registerAndGetToken("sr-lvl79-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Level 79 Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 23);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());
        assertThat(result.scorePercentage()).isEqualTo(77); // 23/30, rounded
        assertThat(result.level()).isEqualTo(AssessmentLevel.INTERMEDIATE);
    }

    // ---- Topic performance / strong / weak (spec §25 items 7-9) ----

    @Test
    void getResult_computesTopicPerformanceAndStrongWeakBuckets() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-topic-admin@example.com");
        String studentToken = registerAndGetToken("sr-topic-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Topic Skill");
        UUID basicsId = createTopic(adminToken, skillId, "Basics");
        UUID oopId = createTopic(adminToken, skillId, "OOP");
        UUID exceptionsId = createTopic(adminToken, skillId, "Exception Handling");
        publishUniformQuestions(adminToken, skillId, basicsId, "Basics", 12);
        publishUniformQuestions(adminToken, skillId, oopId, "OOP", 12);
        publishUniformQuestions(adminToken, skillId, exceptionsId, "Exceptions", 6);

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        // Deterministic per-topic correctness: 11/12 Basics correct (91.7% -> STRONG), 8/12 OOP
        // correct (66.7% -> DEVELOPING), 2/6 Exceptions correct (33.3% -> NEEDS_IMPROVEMENT).
        int basicsCorrect = 0;
        int oopCorrect = 0;
        int exceptionsCorrect = 0;
        for (AssessmentQuestionView q : assessment.questions()) {
            String text = q.questionText();
            boolean giveCorrect;
            if (text.startsWith("Basics")) {
                giveCorrect = basicsCorrect < 11;
                if (giveCorrect) basicsCorrect++;
            } else if (text.startsWith("OOP")) {
                giveCorrect = oopCorrect < 8;
                if (giveCorrect) oopCorrect++;
            } else {
                giveCorrect = exceptionsCorrect < 2;
                if (giveCorrect) exceptionsCorrect++;
            }
            answer(studentToken, assessment.id(), q.id(), optionIdByText(q, giveCorrect ? "Option A" : "Option B"));
        }
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.scorePercentage()).isEqualTo(70); // (11+8+2)/30 = 70%
        assertThat(result.level()).isEqualTo(AssessmentLevel.INTERMEDIATE);
        assertThat(result.topicPerformance()).hasSize(3);

        TopicPerformanceView basics = findTopic(result, "Basics");
        assertThat(basics.correctCount()).isEqualTo(11);
        assertThat(basics.totalQuestions()).isEqualTo(12);
        assertThat(basics.percentage()).isEqualTo(92);
        assertThat(basics.tier()).isEqualTo(TopicPerformanceTier.STRONG);

        TopicPerformanceView oop = findTopic(result, "OOP");
        assertThat(oop.percentage()).isEqualTo(67);
        assertThat(oop.tier()).isEqualTo(TopicPerformanceTier.DEVELOPING);

        TopicPerformanceView exceptions = findTopic(result, "Exception Handling");
        assertThat(exceptions.percentage()).isEqualTo(33);
        assertThat(exceptions.tier()).isEqualTo(TopicPerformanceTier.NEEDS_IMPROVEMENT);

        assertThat(result.summary().strongTopics()).containsExactly("Basics");
        assertThat(result.summary().needsImprovementTopics()).containsExactly("Exception Handling");
    }

    private TopicPerformanceView findTopic(AssessmentResultSummaryResponse result, String name) {
        return result.topicPerformance().stream().filter(t -> t.topicName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No topic performance entry named " + name));
    }

    @Test
    void getResult_questionsWithoutTopic_groupUnderGeneral() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-notopic-admin@example.com");
        String studentToken = registerAndGetToken("sr-notopic-student@example.com");
        UUID skillId = createSkill(adminToken, "SR No Topic Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 15);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.topicPerformance()).hasSize(1);
        assertThat(result.topicPerformance().get(0).topicId()).isNull();
        assertThat(result.topicPerformance().get(0).topicName()).isEqualTo("General");
        assertThat(result.topicPerformance().get(0).totalQuestions()).isEqualTo(30);
    }

    // ---- Tag-wise performance (bug fix: Question.tags was never read by assessment analysis;
    // AssessmentQuestion now snapshots tags at generation time — see assessment_question_tags /
    // AssessmentQuestion.tags / SkillResultService.buildSummary) ----

    @Test
    void getResult_taggedQuestions_groupByActualTagNamesWithCorrectTiers() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-tag-admin@example.com");
        String studentToken = registerAndGetToken("sr-tag-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Tag Skill");
        publishQuestionsWithTag(adminToken, skillId, "Functions", 12, "python-functions");
        publishQuestionsWithTag(adminToken, skillId, "Loops", 12, "python-loops");
        publishQuestionsWithTag(adminToken, skillId, "Oop", 6, "python-oop");

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        // Same deterministic per-bucket correctness as the topic-based equivalent test above:
        // 11/12 functions (91.7% -> STRONG), 8/12 loops (66.7% -> DEVELOPING), 2/6 oop (33.3% -> NEEDS_IMPROVEMENT).
        int functionsCorrect = 0;
        int loopsCorrect = 0;
        int oopCorrect = 0;
        for (AssessmentQuestionView q : assessment.questions()) {
            String text = q.questionText();
            boolean giveCorrect;
            if (text.startsWith("Functions")) {
                giveCorrect = functionsCorrect < 11;
                if (giveCorrect) functionsCorrect++;
            } else if (text.startsWith("Loops")) {
                giveCorrect = loopsCorrect < 8;
                if (giveCorrect) loopsCorrect++;
            } else {
                giveCorrect = oopCorrect < 2;
                if (giveCorrect) oopCorrect++;
            }
            answer(studentToken, assessment.id(), q.id(), optionIdByText(q, giveCorrect ? "Option A" : "Option B"));
        }
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.scorePercentage()).isEqualTo(70); // (11+8+2)/30 = 70%, unchanged formula
        assertThat(result.topicPerformance()).hasSize(3);

        TopicPerformanceView functions = findTopic(result, "python-functions");
        assertThat(functions.topicId()).isNull(); // tags have no UUID identity, unlike topics
        assertThat(functions.correctCount()).isEqualTo(11);
        assertThat(functions.totalQuestions()).isEqualTo(12);
        assertThat(functions.percentage()).isEqualTo(92);
        assertThat(functions.tier()).isEqualTo(TopicPerformanceTier.STRONG);

        TopicPerformanceView loops = findTopic(result, "python-loops");
        assertThat(loops.percentage()).isEqualTo(67);
        assertThat(loops.tier()).isEqualTo(TopicPerformanceTier.DEVELOPING);

        TopicPerformanceView oop = findTopic(result, "python-oop");
        assertThat(oop.percentage()).isEqualTo(33);
        assertThat(oop.tier()).isEqualTo(TopicPerformanceTier.NEEDS_IMPROVEMENT);

        assertThat(result.summary().strongTopics()).containsExactly("python-functions");
        assertThat(result.summary().needsImprovementTopics()).containsExactly("python-oop");
        // "python-loops" sits in the DEVELOPING tier, which is intentionally neither strong nor weak.
        assertThat(result.summary().strongTopics()).doesNotContain("python-loops");
        assertThat(result.summary().needsImprovementTopics()).doesNotContain("python-loops");
    }

    @Test
    void getResult_taggedQuestionWithNoTopic_neverFallsToGeneral() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-tagnotopic-admin@example.com");
        String studentToken = registerAndGetToken("sr-tagnotopic-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Tag No Topic Skill");
        // Every question is tagged and every question's topicId is null (publishQuestionWithTags
        // never assigns a topic) — this is exactly the shape that used to collapse into "General"
        // before the fix, since the old code grouped by topicId alone.
        publishQuestionsWithTag(adminToken, skillId, "Q", 30, "python-functions");

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 18);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.topicPerformance()).hasSize(1);
        assertThat(result.topicPerformance().get(0).topicName()).isEqualTo("python-functions");
        assertThat(result.topicPerformance().get(0).topicName()).isNotEqualTo("General");
        assertThat(result.topicPerformance().get(0).totalQuestions()).isEqualTo(30);
        assertThat(result.topicPerformance().get(0).correctCount()).isEqualTo(18);
    }

    @Test
    void getResult_mixedTaggedTopicOnlyAndUntaggedQuestions_allThreeBucketKindsCoexist() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-mixed-admin@example.com");
        String studentToken = registerAndGetToken("sr-mixed-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Mixed Skill");
        UUID legacyTopicId = createTopic(adminToken, skillId, "Legacy Topic");
        publishQuestionsWithTag(adminToken, skillId, "Tagged", 12, "python-functions"); // tag bucket
        publishUniformQuestions(adminToken, skillId, legacyTopicId, "Legacy", 12); // topic-fallback bucket, no tags
        publishUniformQuestions(adminToken, skillId, null, "Plain", 6); // General bucket, no tag and no topic

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        int taggedCorrect = 0;
        int legacyCorrect = 0;
        int plainCorrect = 0;
        for (AssessmentQuestionView q : assessment.questions()) {
            String text = q.questionText();
            boolean giveCorrect;
            if (text.startsWith("Tagged")) {
                giveCorrect = taggedCorrect < 9;
                if (giveCorrect) taggedCorrect++;
            } else if (text.startsWith("Legacy")) {
                giveCorrect = legacyCorrect < 6;
                if (giveCorrect) legacyCorrect++;
            } else {
                giveCorrect = plainCorrect < 3;
                if (giveCorrect) plainCorrect++;
            }
            answer(studentToken, assessment.id(), q.id(), optionIdByText(q, giveCorrect ? "Option A" : "Option B"));
        }
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.correctCount()).isEqualTo(18); // 9+6+3, matches overall 60%
        assertThat(result.topicPerformance()).hasSize(3);

        TopicPerformanceView tagged = findTopic(result, "python-functions");
        assertThat(tagged.totalQuestions()).isEqualTo(12);
        assertThat(tagged.correctCount()).isEqualTo(9);

        TopicPerformanceView legacy = findTopic(result, "Legacy Topic");
        assertThat(legacy.totalQuestions()).isEqualTo(12);
        assertThat(legacy.correctCount()).isEqualTo(6);
        assertThat(legacy.topicId()).isEqualTo(legacyTopicId); // topic-fallback bucket keeps its real id

        TopicPerformanceView general = findTopic(result, "General");
        assertThat(general.totalQuestions()).isEqualTo(6);
        assertThat(general.correctCount()).isEqualTo(3);
        assertThat(general.topicId()).isNull();
    }

    @Test
    void getResult_tagEditedAfterAssessmentTaken_historicalResultKeepsSnapshottedTag() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-tagsnap-admin@example.com");
        String studentToken = registerAndGetToken("sr-tagsnap-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Tag Snapshot Skill");
        UUID taggedQuestionId = publishQuestionWithTags(adminToken, skillId, "TaggedForSnapshot?", "python-functions");
        publishUniformQuestions(adminToken, skillId, null, "Filler", 29);

        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        for (AssessmentQuestionView q : assessment.questions()) {
            boolean correct = q.questionText().equals("TaggedForSnapshot?");
            answer(studentToken, assessment.id(), q.id(), optionIdByText(q, correct ? "Option A" : "Option B"));
        }
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse before = getResult(studentToken, assessment.id());
        TopicPerformanceView beforeBucket = findTopic(before, "python-functions");
        assertThat(beforeBucket.totalQuestions()).isEqualTo(1);
        assertThat(beforeBucket.correctCount()).isEqualTo(1);

        // No endpoint lets an admin retag a PUBLISHED question directly — mutate the row to prove
        // the snapshot (assessment_question_tags), not live Question.tags, is what the result reads.
        Question question = questionRepository.findById(taggedQuestionId).orElseThrow();
        question.setTag("python-loops");
        questionRepository.save(question);

        AssessmentResultSummaryResponse after = getResult(studentToken, assessment.id());
        TopicPerformanceView afterBucket = findTopic(after, "python-functions");
        assertThat(afterBucket.totalQuestions()).isEqualTo(1);
        assertThat(afterBucket.correctCount()).isEqualTo(1);
        assertThat(after.topicPerformance().stream().noneMatch(t -> t.topicName().equals("python-loops"))).isTrue();
    }

    // ---- Historical integrity (spec §25 items 10, 17, 18) ----

    @Test
    void getResult_topicRenamedAfterward_historicalResultKeepsOriginalName() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-rename-admin@example.com");
        String studentToken = registerAndGetToken("sr-rename-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Rename Skill");
        UUID topicId = createTopic(adminToken, skillId, "Original Topic Name");
        publishUniformQuestions(adminToken, skillId, topicId, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 15);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse before = getResult(studentToken, assessment.id());
        assertThat(before.topicPerformance().get(0).topicName()).isEqualTo("Original Topic Name");

        // No rename endpoint exists yet (Phase 7.1 topics are create+list only) — mutate the row
        // directly to prove the snapshot, not the live Topic FK, is what the result actually reads.
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        topic.setName("Renamed Topic");
        topicRepository.save(topic);

        AssessmentResultSummaryResponse after = getResult(studentToken, assessment.id());
        assertThat(after.topicPerformance().get(0).topicName()).isEqualTo("Original Topic Name");
    }

    @Test
    void getResult_questionArchivedAfterward_historicalResultUnchanged() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-archive-admin@example.com");
        String studentToken = registerAndGetToken("sr-archive-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Archive Skill");
        List<UUID> questionIds = publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 18);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse before = getResult(studentToken, assessment.id());

        mockMvc.perform(post("/api/v1/admin/questions/" + questionIds.get(0) + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        AssessmentResultSummaryResponse after = getResult(studentToken, assessment.id());
        assertThat(after.scorePercentage()).isEqualTo(before.scorePercentage());
        assertThat(after.correctCount()).isEqualTo(before.correctCount());
        assertThat(after.level()).isEqualTo(before.level());
    }

    @Test
    void getResult_questionNewVersionPublishedAfterward_historicalResultUnchanged() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-version-admin@example.com");
        String studentToken = registerAndGetToken("sr-version-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Version Skill");
        List<UUID> questionIds = publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 21);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse before = getResult(studentToken, assessment.id());

        String newVersionBody = mockMvc.perform(post("/api/v1/admin/questions/" + questionIds.get(0) + "/new-version")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID newVersionId = objectMapper.readValue(newVersionBody, QuestionResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/questions/" + newVersionId + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/questions/" + newVersionId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        AssessmentResultSummaryResponse after = getResult(studentToken, assessment.id());
        assertThat(after.scorePercentage()).isEqualTo(before.scorePercentage());
        assertThat(after.correctCount()).isEqualTo(before.correctCount());
    }

    // ---- Multiple attempts / latest result (spec §25 items 11, 12) ----

    @Test
    void multipleAttempts_bothPersistAndLatestResultReflectsMostRecent() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-retake-admin@example.com");
        String studentToken = registerAndGetToken("sr-retake-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Retake Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);

        AssessmentDetailResponse first = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, first, 12); // 40% -> DEVELOPING
        submit(studentToken, first.id());

        AssessmentDetailResponse second = startAssessment(studentToken, skillId);
        assertThat(second.id()).isNotEqualTo(first.id());
        answerExactlyNCorrect(studentToken, second, 24); // 80% -> ADVANCED
        submit(studentToken, second.id());

        // Both attempts remain independently readable with their own original scores.
        assertThat(getResult(studentToken, first.id()).scorePercentage()).isEqualTo(40);
        assertThat(getResult(studentToken, second.id()).scorePercentage()).isEqualTo(80);

        String latestBody = mockMvc.perform(get("/api/v1/assessments/latest-result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessmentResultSummaryResponse latest = objectMapper.readValue(latestBody, AssessmentResultSummaryResponse.class);
        assertThat(latest.assessmentId()).isEqualTo(second.id());
        assertThat(latest.scorePercentage()).isEqualTo(80);

        String latestForSkillBody = mockMvc.perform(get("/api/v1/assessments/skills/" + skillId + "/latest-result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessmentResultSummaryResponse latestForSkill = objectMapper.readValue(latestForSkillBody,
                AssessmentResultSummaryResponse.class);
        assertThat(latestForSkill.assessmentId()).isEqualTo(second.id());
    }

    @Test
    void latestResult_noCompletedAssessments_returnsNoContent() throws Exception {
        String studentToken = registerAndGetToken("sr-nolatest-student@example.com");

        mockMvc.perform(get("/api/v1/assessments/latest-result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());
    }

    // ---- Ownership / IDOR / state gating (spec §25 items 13-16) ----

    @Test
    void getResult_asDifferentStudent_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-idor-admin@example.com");
        String studentA = registerAndGetToken("sr-idor-a@example.com");
        String studentB = registerAndGetToken("sr-idor-b@example.com");
        UUID skillId = createSkill(adminToken, "SR IDOR Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentA, skillId);
        answerExactlyNCorrect(studentA, assessment, 15);
        submit(studentA, assessment.id());

        mockMvc.perform(get("/api/v1/assessments/" + assessment.id() + "/result")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getResult_whileInProgress_returnsConflict() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-inprogress-admin@example.com");
        String studentToken = registerAndGetToken("sr-inprogress-student@example.com");
        UUID skillId = createSkill(adminToken, "SR In Progress Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);

        mockMvc.perform(get("/api/v1/assessments/" + assessment.id() + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
    }

    @Test
    void getResult_afterSubmission_returnsFullSummary() throws Exception {
        String adminToken = registerAdminAndGetToken("sr-full-admin@example.com");
        String studentToken = registerAndGetToken("sr-full-student@example.com");
        UUID skillId = createSkill(adminToken, "SR Full Skill");
        publishUniformQuestions(adminToken, skillId, null, "Q", 30);
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerExactlyNCorrect(studentToken, assessment, 30);
        submit(studentToken, assessment.id());

        AssessmentResultSummaryResponse result = getResult(studentToken, assessment.id());

        assertThat(result.assessmentId()).isEqualTo(assessment.id());
        assertThat(result.skillId()).isEqualTo(skillId);
        assertThat(result.skillName()).isEqualTo("SR Full Skill");
        assertThat(result.status()).isEqualTo(AssessmentStatus.SUBMITTED);
        assertThat(result.totalQuestions()).isEqualTo(30);
    }

    @Test
    void getResult_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/assessments/" + UUID.randomUUID() + "/result"))
                .andExpect(status().isUnauthorized());
    }
}
