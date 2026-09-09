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
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionResponse;
import com.studen.resource.ResourceCardResponse;
import com.studen.resource.ResourceDetailResponse;
import com.studen.resource.ResourceRequest;
import com.studen.resource.ResourceSkillMappingRequest;
import com.studen.resource.ResourceType;
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
 * Phase 4: Placement -> My Learning integration. Reuses the same register/skill/role/question/
 * assessment helper pattern as {@link PlacementReadinessControllerTest}, plus resource creation
 * from {@code AdminResourceControllerTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementLearningPlanControllerTest {

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
                  "tag": "placement-learning-plan-test",
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

    private PlacementAssessmentDetailResponse createAssessment(String adminToken, UUID roleId, String title)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAssessmentRequest(title,
                                "Readiness assessment", roleId, Difficulty.MEDIUM, null, 60))))
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
                                new PlacementAssessmentQuestionRequest(questionId, null, order, 1))))
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

    private void submit(String token, UUID attemptId) throws Exception {
        mockMvc.perform(post("/api/v1/placement/readiness/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private UUID createPublishedResource(String adminToken, UUID skillId, String title) throws Exception {
        ResourceRequest request = new ResourceRequest(title, "desc", ResourceType.EXTERNAL_LINK, skillId,
                Difficulty.EASY, 10, "https://example.com/" + title.replace(" ", "-"), null, List.of());
        String body = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, ResourceDetailResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/resources/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return id;
    }

    private void mapAdditionalSkill(String adminToken, UUID resourceId, UUID skillId) throws Exception {
        mockMvc.perform(put("/api/v1/admin/resources/" + resourceId + "/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResourceSkillMappingRequest(List.of(skillId)))))
                .andExpect(status().isOk());
    }

    private PlacementLearningPlanResponse getPlan(String studentToken) throws Exception {
        String body = mockMvc.perform(get("/api/v1/placement/learning-plan")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementLearningPlanResponse.class);
    }

    // ---- State machine ----

    @Test
    void plan_noProfile_returnsNoReadinessAssessmentState() throws Exception {
        String studentToken = registerAndGetToken("lp-noprofile-student@example.com");

        PlacementLearningPlanResponse plan = getPlan(studentToken);
        assertThat(plan.state()).isEqualTo(PlacementLearningPlanState.NO_READINESS_ASSESSMENT);
        assertThat(plan.prioritySkills()).isEmpty();
    }

    @Test
    void plan_noCompletedAttempt_returnsNoReadinessAssessmentState() throws Exception {
        String adminToken = registerAdminAndGetToken("lp-noattempt-admin@example.com");
        String studentToken = registerAndGetToken("lp-noattempt-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "LP No Attempt Role");
        saveProfile(studentToken, role.id());

        PlacementLearningPlanResponse plan = getPlan(studentToken);
        assertThat(plan.state()).isEqualTo(PlacementLearningPlanState.NO_READINESS_ASSESSMENT);
    }

    @Test
    void plan_allSkillsStrong_returnsNoSkillGapsState() throws Exception {
        String adminToken = registerAdminAndGetToken("lp-nogaps-admin@example.com");
        String studentToken = registerAndGetToken("lp-nogaps-student@example.com");
        UUID skillId = createSkill(adminToken, "LP No Gaps Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "LP No Gaps Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "NoGaps", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "LP No Gaps Readiness");
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "NoGaps", 10); // 100% -> Strong
        submit(studentToken, attempt.id());

        PlacementLearningPlanResponse plan = getPlan(studentToken);
        assertThat(plan.state()).isEqualTo(PlacementLearningPlanState.NO_SKILL_GAPS);
        assertThat(plan.prioritySkills()).isEmpty();
    }

    @Test
    void plan_gapsWithNoMappedResources_returnsGapsWithoutResourcesState() throws Exception {
        String adminToken = registerAdminAndGetToken("lp-noresources-admin@example.com");
        String studentToken = registerAndGetToken("lp-noresources-student@example.com");
        UUID skillId = createSkill(adminToken, "LP No Resources Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "LP No Resources Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "NoRes", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "LP No Resources Readiness");
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "NoRes", 2); // 20% -> Critical gap, no resources exist
        submit(studentToken, attempt.id());

        PlacementLearningPlanResponse plan = getPlan(studentToken);
        assertThat(plan.state()).isEqualTo(PlacementLearningPlanState.GAPS_WITHOUT_RESOURCES);
        assertThat(plan.prioritySkills()).hasSize(1);
        assertThat(plan.prioritySkills().get(0).resources()).isEmpty();
    }

    // ---- Happy path: ranking, primary + additional skill matching, dedup ----

    @Test
    void plan_withMappedResources_ordersByGapRankAndMatchesPrimaryAndAdditionalSkills() throws Exception {
        String adminToken = registerAdminAndGetToken("lp-flow-admin@example.com");
        String studentToken = registerAndGetToken("lp-flow-student@example.com");

        UUID criticalSkill = createSkill(adminToken, "LP Flow Critical Skill");
        UUID improveSkill = createSkill(adminToken, "LP Flow Improve Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "LP Flow Role");
        mapRoleSkill(adminToken, role.id(), criticalSkill, 1, null, 1);
        mapRoleSkill(adminToken, role.id(), improveSkill, 1, null, 2);

        List<QuestionResponse> criticalQuestions = createPublishedQuestions(adminToken, criticalSkill, "Crit", 10);
        List<QuestionResponse> improveQuestions = createPublishedQuestions(adminToken, improveSkill, "Imp", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "LP Flow Readiness");
        int order = 0;
        for (QuestionResponse q : criticalQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        for (QuestionResponse q : improveQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        // A resource whose *primary* skill is the critical gap.
        UUID criticalResource = createPublishedResource(adminToken, criticalSkill, "LP Flow Critical Course");
        // A resource mapped to the improve gap only via the additional-skills join table.
        UUID unrelatedSkill = createSkill(adminToken, "LP Flow Unrelated Primary Skill");
        UUID improveResource = createPublishedResource(adminToken, unrelatedSkill, "LP Flow Improve Course");
        mapAdditionalSkill(adminToken, improveResource, improveSkill);
        // An unrelated resource for neither gap skill must never appear.
        createPublishedResource(adminToken, unrelatedSkill, "LP Flow Unrelated Course");

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "Crit", 2); // 20% -> Critical
        answerBySkillPrefix(studentToken, attempt, "Imp", 6); // 60% -> Improve
        submit(studentToken, attempt.id());

        PlacementLearningPlanResponse plan = getPlan(studentToken);
        assertThat(plan.state()).isEqualTo(PlacementLearningPlanState.HAS_RECOMMENDATIONS);
        assertThat(plan.targetRoleId()).isEqualTo(role.id());
        assertThat(plan.prioritySkills()).hasSize(2);

        PlacementSkillPlanResponse first = plan.prioritySkills().get(0);
        assertThat(first.skillId()).isEqualTo(criticalSkill);
        assertThat(first.status()).isEqualTo(SkillReadinessStatus.CRITICAL);
        assertThat(first.resources()).extracting(ResourceCardResponse::id).containsExactly(criticalResource);

        PlacementSkillPlanResponse second = plan.prioritySkills().get(1);
        assertThat(second.skillId()).isEqualTo(improveSkill);
        assertThat(second.resources()).extracting(ResourceCardResponse::id).containsExactly(improveResource);

        // The unrelated resource never appears anywhere in the plan.
        List<UUID> allResourceIds = plan.prioritySkills().stream()
                .flatMap(s -> s.resources().stream()).map(ResourceCardResponse::id).toList();
        assertThat(allResourceIds).containsExactlyInAnyOrder(criticalResource, improveResource);
    }

    @Test
    void plan_resourceMappedToTwoGapSkills_appearsOnlyOnceUnderHigherPriorityGap() throws Exception {
        String adminToken = registerAdminAndGetToken("lp-dedup-admin@example.com");
        String studentToken = registerAndGetToken("lp-dedup-student@example.com");

        UUID criticalSkill = createSkill(adminToken, "LP Dedup Critical Skill");
        UUID improveSkill = createSkill(adminToken, "LP Dedup Improve Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "LP Dedup Role");
        mapRoleSkill(adminToken, role.id(), criticalSkill, 1, null, 1);
        mapRoleSkill(adminToken, role.id(), improveSkill, 1, null, 2);

        List<QuestionResponse> criticalQuestions = createPublishedQuestions(adminToken, criticalSkill, "DCrit", 10);
        List<QuestionResponse> improveQuestions = createPublishedQuestions(adminToken, improveSkill, "DImp", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "LP Dedup Readiness");
        int order = 0;
        for (QuestionResponse q : criticalQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        for (QuestionResponse q : improveQuestions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        // One resource, primary skill = critical gap, also additionally mapped to the improve gap.
        UUID sharedResource = createPublishedResource(adminToken, criticalSkill, "LP Dedup Bootcamp");
        mapAdditionalSkill(adminToken, sharedResource, improveSkill);

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "DCrit", 2); // Critical
        answerBySkillPrefix(studentToken, attempt, "DImp", 6); // Improve
        submit(studentToken, attempt.id());

        PlacementLearningPlanResponse plan = getPlan(studentToken);
        assertThat(plan.prioritySkills().get(0).resources()).extracting(ResourceCardResponse::id)
                .containsExactly(sharedResource);
        // Not repeated under the lower-priority (improve) gap.
        assertThat(plan.prioritySkills().get(1).resources()).isEmpty();
    }

    // ---- Per-skill endpoint + security ----

    @Test
    void skillResources_forSkillNotInCurrentGaps_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("lp-notgap-admin@example.com");
        String studentToken = registerAndGetToken("lp-notgap-student@example.com");
        UUID skillId = createSkill(adminToken, "LP Not Gap Skill");
        PlacementRoleDetailResponse role = createRole(adminToken, "LP Not Gap Role");
        mapRoleSkill(adminToken, role.id(), skillId, 1, null, 1);
        List<QuestionResponse> questions = createPublishedQuestions(adminToken, skillId, "NotGap", 10);
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "LP Not Gap Readiness");
        int order = 0;
        for (QuestionResponse q : questions) {
            addQuestionToAssessment(adminToken, assessment.id(), q.id(), order++);
        }
        publishAssessment(adminToken, assessment.id());
        saveProfile(studentToken, role.id());

        PlacementAttemptDetailResponse attempt = startAttempt(studentToken);
        answerBySkillPrefix(studentToken, attempt, "NotGap", 10); // 100% -> Strong, no gap
        submit(studentToken, attempt.id());

        UUID unrelatedSkill = createSkill(adminToken, "LP Not Gap Unrelated Skill");
        mockMvc.perform(get("/api/v1/placement/learning-plan/skills/" + unrelatedSkill + "/resources")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void learningPlanEndpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/v1/placement/learning-plan")).andExpect(status().isUnauthorized());
    }
}
