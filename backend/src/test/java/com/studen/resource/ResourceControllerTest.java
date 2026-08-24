package com.studen.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.assessment.AnswerRequest;
import com.studen.assessment.AssessmentDetailResponse;
import com.studen.assessment.AssessmentQuestionView;
import com.studen.assessment.StartAssessmentRequest;
import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.EligibilityState;
import com.studen.portfolio.PortfolioRequest;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionOptionRequest;
import com.studen.questionbank.QuestionRequest;
import com.studen.questionbank.QuestionResponse;
import com.studen.questionbank.QuestionType;
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
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class ResourceControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Resource Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private void createPortfolio(String token, Set<UUID> skillIds) throws Exception {
        PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null, skillIds, null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private List<UUID> publishQuestionsWithTag(String adminToken, UUID skillId, String prefix, int count, String tag)
            throws Exception {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuestionRequest request = new QuestionRequest(skillId, null, prefix + " question " + i + "?", QuestionType.MCQ_SINGLE,
                    Difficulty.EASY, "Explanation", null, Set.of(tag), List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
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
            ids.add(id);
        }
        return ids;
    }

    private AssessmentDetailResponse startAssessment(String token, UUID skillId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(skillId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AssessmentDetailResponse.class);
    }

    private UUID optionIdByText(AssessmentQuestionView question, String text) {
        return question.options().stream().filter(o -> o.optionText().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("No option with text " + text)).id();
    }

    private void answerAllWrong(String token, AssessmentDetailResponse assessment) throws Exception {
        for (AssessmentQuestionView q : assessment.questions()) {
            mockMvc.perform(patch("/api/v1/assessments/" + assessment.id() + "/questions/" + q.id() + "/answer")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(optionIdByText(q, "Option B"))))))
                    .andExpect(status().isOk());
        }
    }

    private void submit(String token, UUID assessmentId) throws Exception {
        mockMvc.perform(post("/api/v1/assessments/" + assessmentId + "/submit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResourceDetailResponse createAndPublishResource(String adminToken, UUID skillId, String title, String... tags)
            throws Exception {
        ResourceRequest request = new ResourceRequest(title, "desc", ResourceType.EXTERNAL_LINK, skillId, Difficulty.EASY,
                10, "https://example.com/" + title.replace(" ", "-"), null, List.of(tags));
        String createdBody = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ResourceDetailResponse created = objectMapper.readValue(createdBody, ResourceDetailResponse.class);
        mockMvc.perform(post("/api/v1/admin/resources/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return created;
    }

    // --- Eligibility gating (mirrors PortfolioSkillProfileService's existing consumers) ---------

    @Test
    void myLearning_noPortfolio_returnsNoPortfolioState() throws Exception {
        String studentToken = registerAndGetToken("res-ml-noportfolio-student@example.com");

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);
        assertThat(response.state()).isEqualTo(EligibilityState.NO_PORTFOLIO);
        assertThat(response.groups()).isEmpty();
    }

    @Test
    void myLearning_portfolioWithNoSkills_returnsNoSkillsState() throws Exception {
        String studentToken = registerAndGetToken("res-ml-noskills-student@example.com");
        createPortfolio(studentToken, Set.of());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);
        assertThat(response.state()).isEqualTo(EligibilityState.NO_SKILLS);
        assertThat(response.groups()).isEmpty();
    }

    // --- Draft/published visibility -------------------------------------------------------------

    @Test
    void get_draftResource_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("res-draft-admin@example.com");
        String studentToken = registerAndGetToken("res-draft-student@example.com");
        UUID skillId = createSkill(adminToken, "Res Draft Skill");
        ResourceRequest request = new ResourceRequest("Draft Resource", "desc", ResourceType.EXTERNAL_LINK, skillId,
                Difficulty.EASY, 10, "https://example.com/draft", null, List.of());
        String createdBody = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(createdBody, ResourceDetailResponse.class).id();

        mockMvc.perform(get("/api/v1/resources/" + id).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    // --- Progress -------------------------------------------------------------------------------

    @Test
    void start_thenComplete_isIdempotentAndTracksTimestamps() throws Exception {
        String adminToken = registerAdminAndGetToken("res-progress-admin@example.com");
        String studentToken = registerAndGetToken("res-progress-student@example.com");
        UUID skillId = createSkill(adminToken, "Res Progress Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Progress Resource");

        String startBody = mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ResourceProgressResponse started = objectMapper.readValue(startBody, ResourceProgressResponse.class);
        assertThat(started.status()).isEqualTo(ResourceProgressStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isNotNull();

        // Idempotent — calling start again doesn't regress/duplicate.
        mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        String completeBody = mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ResourceProgressResponse completed = objectMapper.readValue(completeBody, ResourceProgressResponse.class);
        assertThat(completed.status()).isEqualTo(ResourceProgressStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();

        // Completing again is a no-op, not an error.
        mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void complete_withoutPriorStart_backfillsStartedAt() throws Exception {
        String adminToken = registerAdminAndGetToken("res-directcomplete-admin@example.com");
        String studentToken = registerAndGetToken("res-directcomplete-student@example.com");
        UUID skillId = createSkill(adminToken, "Res Direct Complete Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Direct Complete Resource");

        String body = mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ResourceProgressResponse completed = objectMapper.readValue(body, ResourceProgressResponse.class);
        assertThat(completed.status()).isEqualTo(ResourceProgressStatus.COMPLETED);
        assertThat(completed.startedAt()).isNotNull();
        assertThat(completed.completedAt()).isNotNull();
    }

    // --- The core end-to-end scenario: weak MCQ tag -> matched resource, ranked above a
    // same-skill/no-tag-match resource, with an unrelated-skill resource never appearing at all. ---

    @Test
    void myLearning_weakTagFromAssessment_matchesTaggedResourceAboveSameSkillResource() throws Exception {
        String adminToken = registerAdminAndGetToken("res-ml-match-admin@example.com");
        String studentToken = registerAndGetToken("res-ml-match-student@example.com");
        UUID skillId = createSkill(adminToken, "Res ML Match Skill");
        UUID otherSkillId = createSkill(adminToken, "Res ML Other Skill");

        // Assessment generation requires app.assessment.default-question-count (20) published
        // questions to be available for the skill — see AssessmentProperties.
        publishQuestionsWithTag(adminToken, skillId, "Weak", 20, "res-ml-weak-tag");

        ResourceDetailResponse exactMatch = createAndPublishResource(adminToken, skillId, "Exact Tag Match", "res-ml-weak-tag");
        ResourceDetailResponse sameSkillOnly = createAndPublishResource(adminToken, skillId, "Same Skill No Tag Match",
                "res-ml-unrelated-tag");
        ResourceDetailResponse unrelatedSkill = createAndPublishResource(adminToken, otherSkillId, "Unrelated Skill Resource",
                "res-ml-weak-tag");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment); // 0% on the only tag bucket -> NEEDS_IMPROVEMENT
        submit(studentToken, assessment.id());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);

        assertThat(response.state()).isEqualTo(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        assertThat(response.groups()).hasSize(1);
        WeakAreaGroupResponse group = response.groups().get(0);
        assertThat(group.skillId()).isEqualTo(skillId);
        assertThat(group.weakTags()).containsExactly("res-ml-weak-tag");

        List<UUID> resourceIds = group.resources().stream().map(ResourceCardResponse::id).toList();
        assertThat(resourceIds).contains(exactMatch.id(), sameSkillOnly.id());
        assertThat(resourceIds).doesNotContain(unrelatedSkill.id());
        // Exact tag match ranks above the same-skill/no-tag-match resource (spec §9).
        assertThat(resourceIds.indexOf(exactMatch.id())).isLessThan(resourceIds.indexOf(sameSkillOnly.id()));
    }
}
