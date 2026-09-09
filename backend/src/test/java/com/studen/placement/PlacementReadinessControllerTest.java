package com.studen.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.assessment.AssessmentLevel;
import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.questionbank.QuestionResponse;
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
 * Phase 3: the readiness-assessment engine. Reuses the register/skill/question helper pattern
 * already established by {@link com.studen.assessment.SkillResultControllerTest} and
 * {@link PlacementContentControllerTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementReadinessControllerTest {

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
                        .content(objectMapper.writeValueAsString(new PlacementRoleRequest(name, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementRoleDetailResponse.class);
    }

    private void mapRoleSkill(String adminToken, UUID roleId, UUID skillId, int weight,
            AssessmentLevel requiredProficiency, int priority) throws Exception {
        mockMvc.perform(post("/api/v1/admin/placement/roles/" + roleId + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RoleSkillRequest(skillId, weight, requiredProficiency, priority))))
                .andExpect(status().isCreated());
    }

    private QuestionResponse createPublishedQuestion(String adminToken, UUID skillId, String text) throws Exception {
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "explanation": "Because A is correct.",
                  "tag": "placement-readiness-test",
                  "options": [
                    {"optionText": "Option A", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "Option B", "displayOrder": 1, "isCorrect": false}
                  ]
                }
                """.formatted(skillId, text);
        String body = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, QuestionResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String published = mockMvc.perform(post("/api/v1/admin/questions/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(published, QuestionResponse.class);
    }

    private List<QuestionResponse> createPublishedQuestions(String adminToken, UUID skillId, String prefix, int count)
            throws Exception {
        List<QuestionResponse> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(createPublishedQuestion(adminToken, skillId, prefix + " question " + i + "?"));
        }
        return result;
    }

    private PlacementAssessmentDetailResponse createAssessment(String adminToken, UUID roleId, String title,
            Integer durationMinutes) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAssessmentRequest(title,
                                "Readiness assessment", roleId, com.studen.questionbank.Difficulty.MEDIUM,
                                durationMinutes, 60))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementAssessmentDetailResponse.class);
    }

    private void addQuestionToAssessment(String adminToken, UUID assessmentId, UUID questionId, int order)
            throws Exception {
        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessmentId + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(questionId, order, 1))))
                .andExpect(status().isCreated());
    }

    private void publishAssessment(String adminToken, UUID assessmentId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessmentId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private void saveProfile(String studentToken, UUID roleId) throws Exception {
        mockMvc.perform(put("/api/v1/placement/profile")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementProfileRequest(roleId,
                                ExperienceLevel.BEGINNER, List.of(), List.of(), List.of(), List.of()))))
                .andExpect(status().isOk());
    }

    private PlacementAttemptDetailResponse startAttempt(String studentToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/placement/readiness/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementAttemptDetailResponse.class);
    }

    private UUID optionIdByText(PlacementAttemptQuestionView question, String text) {
        return question.options().stream()
                .filter(o -> o.optionText().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No option with text " + text))
                .id();
    }

    private void answer(String token, UUID attemptId, UUID attemptQuestionId, UUID optionId) throws Exception {
        mockMvc.perform(patch("/api/v1/placement/readiness/attempts/" + attemptId + "/questions/"
                        + attemptQuestionId + "/answer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAnswerRequest(List.of(optionId)))))
                .andExpect(status().isOk());
    }

    // Answers the questions belonging to `skillName` (matched by question text prefix) with
    // `correctCount` correct answers, the rest wrong — deterministic control over the per-skill score.
    private void answerBySkillPrefix(String token, PlacementAttemptDetailResponse attempt, String prefix,
            int correctCount) throws Exception {
        int given = 0;
        for (PlacementAttemptQuestionView q : attempt.questions()) {
            if (!q.questionText().startsWith(prefix)) {
                continue;
            }
            boolean giveCorrect = given < correctCount;
            if (giveCorrect) {
                given++;
            }
            answer(token, attempt.id(), q.id(), optionIdByText(q, giveCorrect ? "Option A" : "Option B"));
        }
    }

    private PlacementAttemptReviewResponse submit(String token, UUID attemptId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/placement/readiness/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementAttemptReviewResponse.class);
    }

    private PlacementReadinessResultResponse getResult(String token, UUID attemptId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/placement/readiness/attempts/" + attemptId + "/result")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementReadinessResultResponse.class);
    }

    // ---- Entry-state edge cases ----

    @Test
    void status_noProfile_returnsNoProfileState() throws Exception {
        String studentToken = registerAndGetToken("pr-noprofile-student@example.com");

        String body = mockMvc.perform(get("/api/v1/placement/readiness/status")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementReadinessStatusResponse response = objectMapper.readValue(body, PlacementReadinessStatusResponse.class);

        assertThat(response.state()).isEqualTo(PlacementReadinessState.NO_PROFILE);
    }

    @Test
    void start_withNoProfile_returnsConflict() throws Exception {
        String studentToken = registerAndGetToken("pr-noprofile-start-student@example.com");

        mockMvc.perform(post("/api/v1/placement/readiness/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
    }

    @Test
    void status_profileButNoPublishedAssessment_returnsNoAssessmentAvailable() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-noassess-admin@example.com");
        String studentToken = registerAndGetToken("pr-noassess-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "No Assessment Role");
        saveProfile(studentToken, role.id());

        String body = mockMvc.perform(get("/api/v1/placement/readiness/status")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementReadinessStatusResponse response = objectMapper.readValue(body, PlacementReadinessStatusResponse.class);

        assertThat(response.state()).isEqualTo(PlacementReadinessState.NO_ASSESSMENT_AVAILABLE);
        assertThat(response.targetRoleId()).isEqualTo(role.id());
    }

    @Test
    void start_withNoPublishedAssessmentForRole_returnsConflict() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-noassess-start-admin@example.com");
        String studentToken = registerAndGetToken("pr-noassess-start-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "No Assessment Start Role");
        saveProfile(studentToken, role.id());

        mockMvc.perform(post("/api/v1/placement/readiness/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
    }

    // ---- Happy path: role-specific assessment, scoring, skill breakdown ----

    @Test
    void fullFlow_scoresOverallAndPerSkillFromActualPerformance() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-flow-admin@example.com");
        String studentToken = registerAndGetToken("pr-flow-student@example.com");

        UUID pythonSkill = createSkill(adminToken, "PR Flow Python");
        UUID sqlSkill = createSkill(adminToken, "PR Flow SQL");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR Flow Data Analyst");
        mapRoleSkill(adminToken, role.id(), pythonSkill, 1, null, 1);
        mapRoleSkill(adminToken, role.id(), sqlSkill, 1, null, 2);

        List<QuestionResponse> pythonQuestions = createPublishedQuestions(adminToken, pythonSkill, "Python", 10);
        List<QuestionResponse> sqlQuestions = createPublishedQuestions(adminToken, sqlSkill, "SQL", 10);

        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "PR Flow Readiness", 45);
        int order = 0;
        for (QuestionResponse q : pythonQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        for (QuestionResponse q : sqlQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());

        saveProfile(studentToken, role.id());

        // Confirm the readiness assessment is role-specific and reported as available.
        String statusBody = mockMvc.perform(get("/api/v1/placement/readiness/status")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementReadinessStatusResponse statusResponse =
                objectMapper.readValue(statusBody, PlacementReadinessStatusResponse.class);
        assertThat(statusResponse.state()).isEqualTo(PlacementReadinessState.ASSESSMENT_AVAILABLE);
        assertThat(statusResponse.assessmentId()).isEqualTo(assessment.id());
        assertThat(statusResponse.questionCount()).isEqualTo(20);

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        assertThat(attempt.totalQuestions()).isEqualTo(20);

        // 9/10 Python correct (90% -> Strong), 6/10 SQL correct (60% -> Improve).
        answerBySkillPrefix(studentToken, attempt, "Python", 9);
        answerBySkillPrefix(studentToken, attempt, "SQL", 6);

        PlacementAttemptReviewResponse review = submit(studentToken, attempt.id());
        assertThat(review.correctCount()).isEqualTo(15);
        assertThat(review.scorePercentage()).isEqualTo(75); // 15/20

        PlacementReadinessResultResponse result = getResult(studentToken, attempt.id());
        assertThat(result.scorePercentage()).isEqualTo(75);
        assertThat(result.correctCount()).isEqualTo(15);
        assertThat(result.skillBreakdown()).hasSize(2);

        PlacementSkillBreakdownView python = result.skillBreakdown().stream()
                .filter(s -> s.skillId().equals(pythonSkill)).findFirst().orElseThrow();
        assertThat(python.correctCount()).isEqualTo(9);
        assertThat(python.totalQuestions()).isEqualTo(10);
        assertThat(python.scorePercentage()).isEqualTo(90);
        assertThat(python.status()).isEqualTo(SkillReadinessStatus.STRONG);

        PlacementSkillBreakdownView sql = result.skillBreakdown().stream()
                .filter(s -> s.skillId().equals(sqlSkill)).findFirst().orElseThrow();
        assertThat(sql.correctCount()).isEqualTo(6);
        assertThat(sql.scorePercentage()).isEqualTo(60);
        assertThat(sql.status()).isEqualTo(SkillReadinessStatus.IMPROVE);

        // Only the non-strong skill is a priority gap.
        assertThat(result.priorityGaps()).hasSize(1);
        assertThat(result.priorityGaps().get(0).skillId()).isEqualTo(sqlSkill);
        assertThat(result.priorityGaps().get(0).rank()).isEqualTo(1);

        // "Latest result" reflects this attempt.
        String latestBody = mockMvc.perform(get("/api/v1/placement/readiness/latest-result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementReadinessResultResponse latest =
                objectMapper.readValue(latestBody, PlacementReadinessResultResponse.class);
        assertThat(latest.attemptId()).isEqualTo(attempt.id());
    }

    // ---- Priority gap ranking respects RoleSkill priority/weight, not just lowest score ----

    @Test
    void priorityGaps_rankedByRoleSkillPriorityNotRawScore() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-gap-admin@example.com");
        String studentToken = registerAndGetToken("pr-gap-student@example.com");

        UUID highPrioritySkill = createSkill(adminToken, "PR Gap High Priority");
        UUID lowPrioritySkill = createSkill(adminToken, "PR Gap Low Priority");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR Gap Role");
        // High-priority skill (priority 1) scores HIGHER than the low-priority skill (priority 5),
        // but both fall below the "Good" threshold — priority must still put it first.
        mapRoleSkill(adminToken, role.id(), highPrioritySkill, 1, null, 1);
        mapRoleSkill(adminToken, role.id(), lowPrioritySkill, 1, null, 5);

        List<QuestionResponse> highQuestions = createPublishedQuestions(adminToken, highPrioritySkill, "High", 10);
        List<QuestionResponse> lowQuestions = createPublishedQuestions(adminToken, lowPrioritySkill, "Low", 10);

        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "PR Gap Readiness", null);
        int order = 0;
        for (QuestionResponse q : highQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        for (QuestionResponse q : lowQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "High", 6); // 60% -> Improve
        answerBySkillPrefix(studentToken, attempt, "Low", 3); // 30% -> Critical (worse score)
        submit(studentToken, attempt.id());

        PlacementReadinessResultResponse result = getResult(studentToken, attempt.id());
        assertThat(result.priorityGaps()).hasSize(2);
        // Critical severity always ranks before Improve, regardless of configured priority.
        assertThat(result.priorityGaps().get(0).skillId()).isEqualTo(lowPrioritySkill);
        assertThat(result.priorityGaps().get(0).status()).isEqualTo(SkillReadinessStatus.CRITICAL);
        assertThat(result.priorityGaps().get(1).skillId()).isEqualTo(highPrioritySkill);
        assertThat(result.priorityGaps().get(1).status()).isEqualTo(SkillReadinessStatus.IMPROVE);
    }

    @Test
    void priorityGaps_samesSeverity_tieBreaksByRoleSkillPriorityThenWeight() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-gap-tie-admin@example.com");
        String studentToken = registerAndGetToken("pr-gap-tie-student@example.com");

        UUID skillA = createSkill(adminToken, "PR Tie Skill A");
        UUID skillB = createSkill(adminToken, "PR Tie Skill B");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR Tie Role");
        // Both land in IMPROVE with different scores, but A has the higher-priority (lower number)
        // mapping — priority must win over score even though B scored worse.
        mapRoleSkill(adminToken, role.id(), skillA, 1, null, 1);
        mapRoleSkill(adminToken, role.id(), skillB, 1, null, 2);

        List<QuestionResponse> aQuestions = createPublishedQuestions(adminToken, skillA, "AA", 10);
        List<QuestionResponse> bQuestions = createPublishedQuestions(adminToken, skillB, "BB", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "PR Tie Readiness", null);
        int order = 0;
        for (QuestionResponse q : aQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        for (QuestionResponse q : bQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "AA", 6); // 60% -> Improve
        answerBySkillPrefix(studentToken, attempt, "BB", 5); // 50% -> Improve, lower score than A
        submit(studentToken, attempt.id());

        PlacementReadinessResultResponse result = getResult(studentToken, attempt.id());
        assertThat(result.priorityGaps()).hasSize(2);
        assertThat(result.priorityGaps().get(0).skillId()).isEqualTo(skillA); // priority 1 wins despite higher score
        assertThat(result.priorityGaps().get(1).skillId()).isEqualTo(skillB);
    }

    // ---- Required proficiency: a "Good"/"Strong" score can still be a gap if below the role's target ----

    @Test
    void skillBelowRequiredProficiency_isStillFlaggedAsGapDespiteGoodScore() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-proficiency-admin@example.com");
        String studentToken = registerAndGetToken("pr-proficiency-student@example.com");

        UUID skillId = createSkill(adminToken, "PR Proficiency Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR Proficiency Role");
        // Role requires EXPERT; a 75% score only reaches INTERMEDIATE (per ScoringProperties
        // defaults) even though 75% is comfortably in the "Good" readiness band.
        mapRoleSkill(adminToken, role.id(), skillId, 1, AssessmentLevel.EXPERT, 1);

        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "Prof", 20);
        PlacementAssessmentDetailResponse assessment =
                createAssessment(adminToken, role.id(), "PR Proficiency Readiness", null);
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "Prof", 15); // 75% -> Good, but INTERMEDIATE level
        submit(studentToken, attempt.id());

        PlacementReadinessResultResponse result = getResult(studentToken, attempt.id());
        PlacementSkillBreakdownView breakdown = result.skillBreakdown().get(0);
        assertThat(breakdown.status()).isEqualTo(SkillReadinessStatus.GOOD);
        assertThat(breakdown.meetsRequiredProficiency()).isFalse();
        assertThat(result.priorityGaps()).hasSize(1);
        assertThat(result.priorityGaps().get(0).skillId()).isEqualTo(skillId);
    }

    // ---- Security / anti-tampering ----

    @Test
    void getResult_asDifferentStudent_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-idor-admin@example.com");
        String studentA = registerAndGetToken("pr-idor-a@example.com");
        String studentB = registerAndGetToken("pr-idor-b@example.com");
        UUID skillId = createSkill(adminToken, "PR IDOR Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR IDOR Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "IDOR", 5);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "PR IDOR Readiness", null);
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentA, role.id());
        saveProfile(studentB, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentA);
        answerBySkillPrefix(studentA, attempt, "IDOR", 5);
        submit(studentA, attempt.id());

        mockMvc.perform(get("/api/v1/placement/readiness/attempts/" + attempt.id() + "/result")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/placement/readiness/attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveAnswer_withOptionFromAnotherQuestion_returnsBadRequest() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-tamper-admin@example.com");
        String studentToken = registerAndGetToken("pr-tamper-student@example.com");
        UUID skillId = createSkill(adminToken, "PR Tamper Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR Tamper Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "Tamper", 3);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "PR Tamper Readiness", null);
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        UUID bogusOptionId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/placement/readiness/attempts/" + attempt.id() + "/questions/"
                        + attempt.questions().get(0).id() + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAnswerRequest(List.of(bogusOptionId)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getResult_whileInProgress_returnsConflict() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-inprogress-admin@example.com");
        String studentToken = registerAndGetToken("pr-inprogress-student@example.com");
        UUID skillId = createSkill(adminToken, "PR In Progress Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR In Progress Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "InProgress", 5);
        PlacementAssessmentDetailResponse assessment =
                createAssessment(adminToken, role.id(), "PR In Progress Readiness", null);
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);

        mockMvc.perform(get("/api/v1/placement/readiness/attempts/" + attempt.id() + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());
    }

    // ---- Retakes preserve history ----

    @Test
    void retake_createsNewAttemptAndPreservesHistoricalResult() throws Exception {
        String adminToken = registerAdminAndGetToken("pr-retake-admin@example.com");
        String studentToken = registerAndGetToken("pr-retake-student@example.com");
        UUID skillId = createSkill(adminToken, "PR Retake Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "PR Retake Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "Retake", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "PR Retake Readiness", null);
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse first = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, first, "Retake", 3); // 30%
        submit(studentToken, first.id());

        PlacementAttemptDetailResponse second = startAttempt(studentToken);
        assertThat(second.id()).isNotEqualTo(first.id());
        answerBySkillPrefix(studentToken, second, "Retake", 8); // 80%
        submit(studentToken, second.id());

        assertThat(getResult(studentToken, first.id()).scorePercentage()).isEqualTo(30);
        assertThat(getResult(studentToken, second.id()).scorePercentage()).isEqualTo(80);

        List<PlacementAttemptSummaryResponse> history = List.of(objectMapper.readValue(
                mockMvc.perform(get("/api/v1/placement/readiness/attempts")
                                .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                PlacementAttemptSummaryResponse[].class));
        assertThat(history).hasSize(2);
    }

    @Test
    void latestResult_noAttempts_returnsNoContent() throws Exception {
        String studentToken = registerAndGetToken("pr-nolatest-student@example.com");

        mockMvc.perform(get("/api/v1/placement/readiness/latest-result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void readinessEndpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/v1/placement/readiness/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/placement/readiness/attempts")).andExpect(status().isUnauthorized());
    }
}
