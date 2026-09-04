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
import java.util.ArrayList;
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

// Mirrors ResourceControllerTest's setup helpers exactly (same repo convention — each controller
// test class owns its own copies rather than a shared base, see CommunicationTestSupport as the
// one deliberate exception elsewhere in this repo).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class RoadmapControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Roadmap Skills"))))
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
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuestionRequest request = new QuestionRequest(skillId, null, prefix + " question " + i + "?", QuestionType.MCQ_SINGLE,
                    Difficulty.EASY, "Explanation", null, tag, List.of(opt("Option A", 0, true), opt("Option B", 1, false)));
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

    private RoadmapResponse getRoadmap(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/roadmap").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, RoadmapResponse.class);
    }

    // --- TEST 1/2/5: multi-topic + composite-tag splitting, no generic language-only fabrication ---

    @Test
    void roadmap_multipleWeakTopics_surfaceAsSeparateOrderedItemsWithRealResources() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-multi-admin@example.com");
        String studentToken = registerAndGetToken("rm-multi-student@example.com");
        UUID skillId = createSkill(adminToken, "Roadmap Multi Skill");

        publishQuestionsWithTag(adminToken, skillId, "Q", 10, "python-lists");
        publishQuestionsWithTag(adminToken, skillId, "Q", 10, "python-loops");
        publishQuestionsWithTag(adminToken, skillId, "Q", 10, "python-dictionaries");

        createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");
        createAndPublishResource(adminToken, skillId, "Python Loops", "python-loops");
        createAndPublishResource(adminToken, skillId, "Python Dictionaries", "python-dictionaries");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        RoadmapResponse roadmap = getRoadmap(studentToken);
        assertThat(roadmap.state()).isEqualTo(EligibilityState.HAS_AVAILABLE_ASSESSMENTS);
        assertThat(roadmap.groups()).hasSize(1);
        List<String> topics = roadmap.groups().get(0).items().stream().map(RoadmapItemResponse::topic).toList();
        assertThat(topics).containsExactlyInAnyOrder("lists", "loops", "dictionaries");
        // Every item must reference its own real resource — never null when one genuinely matches.
        assertThat(roadmap.groups().get(0).items()).allSatisfy(item -> assertThat(item.resource()).isNotNull());
    }

    @Test
    void roadmap_compositeTag_splitsIntoIndividualTopicItems() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-composite-admin@example.com");
        String studentToken = registerAndGetToken("rm-composite-student@example.com");
        UUID skillId = createSkill(adminToken, "Roadmap Composite Skill");

        publishQuestionsWithTag(adminToken, skillId, "Q", 30, "python-list-loops");
        createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");
        createAndPublishResource(adminToken, skillId, "Python Loops", "python-loops");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        RoadmapResponse roadmap = getRoadmap(studentToken);
        List<String> topics = roadmap.groups().get(0).items().stream().map(RoadmapItemResponse::topic).toList();
        assertThat(topics).containsExactlyInAnyOrder("list", "loops");
    }

    @Test
    void roadmap_functionsDefaultArguments_extractsThreeSeparateTopics() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-funcdefault-admin@example.com");
        String studentToken = registerAndGetToken("rm-funcdefault-student@example.com");
        UUID skillId = createSkill(adminToken, "Roadmap FuncDefault Skill");

        publishQuestionsWithTag(adminToken, skillId, "Q", 30, "python-functions-default-arguments");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        RoadmapResponse roadmap = getRoadmap(studentToken);
        List<String> topics = roadmap.groups().get(0).items().stream().map(RoadmapItemResponse::topic).toList();
        assertThat(topics).containsExactlyInAnyOrder("functions", "default", "arguments");
    }

    // --- TEST 6: a Java-only weakness must never surface Python resources, and vice versa. ---

    @Test
    void roadmap_javaWeakness_neverIncludesPythonSkillGroup() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-java-admin@example.com");
        String studentToken = registerAndGetToken("rm-java-student@example.com");
        UUID javaSkillId = createSkill(adminToken, "Roadmap Java Skill");
        UUID pythonSkillId = createSkill(adminToken, "Roadmap Python Skill");

        publishQuestionsWithTag(adminToken, javaSkillId, "Q", 30, "java-collections");
        createAndPublishResource(adminToken, javaSkillId, "Java Collections", "java-collections");
        createAndPublishResource(adminToken, pythonSkillId, "Python Basics", "python-basics");

        createPortfolio(studentToken, Set.of(javaSkillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, javaSkillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        RoadmapResponse roadmap = getRoadmap(studentToken);
        assertThat(roadmap.groups()).hasSize(1);
        assertThat(roadmap.groups().get(0).skillId()).isEqualTo(javaSkillId);
        assertThat(roadmap.groups().get(0).items().get(0).resource().title()).isEqualTo("Java Collections");
    }

    // --- TEST 4: completing the top recommendation's resource advances "next up" to the next
    // weak topic, and never recommends an already-completed topic again. ---

    @Test
    void roadmap_completingTopResource_advancesNextUpToNextWeakTopic() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-advance-admin@example.com");
        String studentToken = registerAndGetToken("rm-advance-student@example.com");
        UUID skillId = createSkill(adminToken, "Roadmap Advance Skill");

        publishQuestionsWithTag(adminToken, skillId, "Q", 15, "python-lists");
        publishQuestionsWithTag(adminToken, skillId, "Q", 15, "python-loops");
        ResourceDetailResponse listsResource = createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");
        createAndPublishResource(adminToken, skillId, "Python Loops", "python-loops");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        RecommendationResponse before = getRecommendations(studentToken);
        assertThat(before.nextUp()).isNotNull();
        String firstTopic = before.nextUp().topic();

        mockMvc.perform(post("/api/v1/resources/" + before.nextUp().resource().id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        RecommendationResponse after = getRecommendations(studentToken);
        assertThat(after.nextUp()).isNotNull();
        assertThat(after.nextUp().topic()).isNotEqualTo(firstTopic);

        // The completed topic must now show COMPLETED status in the full roadmap, never surfacing
        // again as a recommendation.
        RoadmapResponse roadmap = getRoadmap(studentToken);
        RoadmapItemResponse completedItem = roadmap.groups().get(0).items().stream()
                .filter(i -> i.topic().equals(firstTopic)).findFirst().orElseThrow();
        assertThat(completedItem.status()).isEqualTo(ResourceProgressStatus.COMPLETED);
        assertThat(listsResource).isNotNull(); // sanity: the fixture resource used above
    }

    private RecommendationResponse getRecommendations(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/roadmap/recommendations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, RecommendationResponse.class);
    }

    // --- TEST 9: no weaknesses at all must never fabricate a recommendation. ---

    @Test
    void roadmap_noWeaknesses_returnsEmptyRoadmapAndNoRecommendation() throws Exception {
        String studentToken = registerAndGetToken("rm-noweak-student@example.com");

        RoadmapResponse roadmap = getRoadmap(studentToken);
        assertThat(roadmap.state()).isEqualTo(EligibilityState.NO_PORTFOLIO);
        assertThat(roadmap.groups()).isEmpty();
        assertThat(roadmap.nextUp()).isNull();

        RecommendationResponse recommendations = getRecommendations(studentToken);
        assertThat(recommendations.nextUp()).isNull();
        assertThat(recommendations.message()).isNotBlank();
    }

    // --- TEST 10: once every matched resource for every weak topic is completed, the roadmap
    // reports full completion and never falls back to an irrelevant recommendation. ---

    @Test
    void roadmap_allWeakTopicResourcesCompleted_reportsFullCompletionWithNoNextUp() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-complete-admin@example.com");
        String studentToken = registerAndGetToken("rm-complete-student@example.com");
        UUID skillId = createSkill(adminToken, "Roadmap Complete Skill");

        publishQuestionsWithTag(adminToken, skillId, "Q", 30, "python-lists");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        RoadmapResponse roadmap = getRoadmap(studentToken);
        assertThat(roadmap.allCaughtUp()).isTrue();
        assertThat(roadmap.nextUp()).isNull();
        assertThat(roadmap.overview().percentage()).isEqualTo(100);

        RecommendationResponse recommendations = getRecommendations(studentToken);
        assertThat(recommendations.nextUp()).isNull();
        assertThat(recommendations.message()).contains("completed");
    }

    @Test
    void progress_returnsSameOverviewAsFullRoadmap() throws Exception {
        String adminToken = registerAdminAndGetToken("rm-progress-admin@example.com");
        String studentToken = registerAndGetToken("rm-progress-student@example.com");
        UUID skillId = createSkill(adminToken, "Roadmap Progress Skill");

        publishQuestionsWithTag(adminToken, skillId, "Q", 30, "python-lists");
        createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        RoadmapResponse roadmap = getRoadmap(studentToken);
        String progressBody = mockMvc.perform(get("/api/v1/roadmap/progress").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RoadmapOverviewResponse progress = objectMapper.readValue(progressBody, RoadmapOverviewResponse.class);
        assertThat(progress).isEqualTo(roadmap.overview());
    }
}
