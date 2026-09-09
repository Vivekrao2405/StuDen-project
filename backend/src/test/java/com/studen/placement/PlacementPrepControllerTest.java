package com.studen.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.practical.CodingLanguage;
import com.studen.practical.EvaluationType;
import com.studen.practical.PracticalAssessmentDetailResponse;
import com.studen.practical.PracticalAssessmentRequest;
import com.studen.practical.PracticalCodingLanguageRequest;
import com.studen.practical.PracticalQuestionRequest;
import com.studen.practical.PracticalTestCaseRequest;
import com.studen.practical.PracticalType;
import com.studen.practical.WorkspaceType;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionResponse;
import com.studen.resource.ResourceDetailResponse;
import com.studen.resource.ResourceRequest;
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
 * Phase 5 (Placement Prep): the student-facing catalog/series/module-item flow built on top of
 * Phase 0's already-existing series/module/item admin authoring. Covers draft/archived invisibility,
 * the three item-type completion paths (question answer, practical start+terminal-attempt, resource
 * start/complete via the existing resource endpoints), roll-up percentages, and Continue Preparation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementPrepControllerTest {

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Placement Prep Skills"))))
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

    private QuestionResponse createPublishedQuestion(String adminToken, UUID skillId, String text) throws Exception {
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "explanation": "Because A is correct.",
                  "tag": "placement-prep-test",
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

    private ResourceDetailResponse createPublishedResource(String adminToken, UUID skillId, String title)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResourceRequest(title, "Reference material",
                                ResourceType.EXTERNAL_LINK, skillId, Difficulty.EASY, 20,
                                "https://example.com/placement-prep", null, List.of("placement-prep-test")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, ResourceDetailResponse.class).id();
        String published = mockMvc.perform(post("/api/v1/admin/resources/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(published, ResourceDetailResponse.class);
    }

    private PracticalAssessmentDetailResponse createPublishedPractical(String adminToken, UUID skillId, String title)
            throws Exception {
        PracticalQuestionRequest question = new PracticalQuestionRequest(null, title, null, null,
                "Given an array, find the longest consecutive sequence.", "N/A", "1 <= N <= 1000", null, 100, 0,
                List.of(new PracticalCodingLanguageRequest(CodingLanguage.JAVA, "public class Main {}")),
                List.of(new PracticalTestCaseRequest("3\n1 2 3", "3", false, 0, null)), null);
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
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String published = mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(published, PracticalAssessmentDetailResponse.class);
    }

    private PlacementSeriesDetailResponse createSeries(String adminToken, UUID roleId, String name,
            List<UUID> skillIds, PreparationType preparationType) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementSeriesRequest(name, "Prep series", null,
                                roleId, CompanyType.SERVICE_BASED, Difficulty.MEDIUM, 20, null, skillIds,
                                preparationType))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementSeriesDetailResponse.class);
    }

    private PlacementModuleResponse createModule(String adminToken, UUID seriesId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series/" + seriesId + "/modules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementModuleRequest(name, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementModuleResponse.class);
    }

    private PlacementModuleItemResponse addItem(String adminToken, UUID moduleId, PlacementModuleItemRequest request)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series/modules/" + moduleId + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementModuleItemResponse.class);
    }

    private void publishSeries(String adminToken, UUID seriesId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/placement/series/" + seriesId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // --- Visibility ----------------------------------------------------------------------------

    @Test
    void draftSeries_isInvisibleToStudents() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prep-draft-admin@example.com");
        String studentToken = registerAndGetToken("pl-prep-draft-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Draft Visibility Role");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Draft Series", null, null);
        createModule(adminToken, series.id(), "Module One");

        mockMvc.perform(get("/api/v1/placement/prep/series/" + series.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        String listBody = mockMvc.perform(get("/api/v1/placement/prep/series")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listBody).doesNotContain(series.id().toString());
    }

    @Test
    void archivedSeries_isInvisibleToStudents() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prep-archived-admin@example.com");
        String studentToken = registerAndGetToken("pl-prep-archived-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Archived Visibility Role");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Archived Series", null, null);
        createModule(adminToken, series.id(), "Module One");
        publishSeries(adminToken, series.id());
        mockMvc.perform(post("/api/v1/admin/placement/series/" + series.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/placement/prep/series/" + series.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishedSeries_isVisibleAndFilterableByPreparationType() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prep-visible-admin@example.com");
        String studentToken = registerAndGetToken("pl-prep-visible-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Visible Role");
        PlacementSeriesDetailResponse series =
                createSeries(adminToken, role.id(), "Visible Series", null, PreparationType.CODING);
        createModule(adminToken, series.id(), "Module One");
        publishSeries(adminToken, series.id());

        String body = mockMvc.perform(get("/api/v1/placement/prep/series")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("preparationType", "CODING"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(series.id().toString());

        String filteredOut = mockMvc.perform(get("/api/v1/placement/prep/series")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("preparationType", "APTITUDE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(filteredOut).doesNotContain(series.id().toString());
    }

    // --- Full completion flow --------------------------------------------------------------------

    @Test
    void fullFlow_answerQuestionStartPracticalAndCompleteResource_updatesProgressAndContinue() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prep-flow-admin@example.com");
        String studentToken = registerAndGetToken("pl-prep-flow-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Flow Role");
        UUID skillId = createSkill(adminToken, "Flow Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Flow question?");
        ResourceDetailResponse resource = createPublishedResource(adminToken, skillId, "Flow Reading");
        PracticalAssessmentDetailResponse practical = createPublishedPractical(adminToken, skillId, "Flow Coding Task");

        PlacementSeriesDetailResponse series =
                createSeries(adminToken, role.id(), "Flow Series", List.of(skillId), PreparationType.FULL_PREPARATION);
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Flow Module");
        PlacementModuleItemResponse questionItem = addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.QUESTION, question.id(), null, null, 0, true));
        PlacementModuleItemResponse resourceItem = addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.RESOURCE, null, null, resource.id(), 1, true));
        PlacementModuleItemResponse practicalItem = addItem(adminToken, module.id(), new PlacementModuleItemRequest(
                ModuleItemType.PRACTICAL_ASSESSMENT, null, practical.id(), null, 2, true));
        publishSeries(adminToken, series.id());

        // Fresh series: nothing started yet.
        String initialDetail = mockMvc.perform(get("/api/v1/placement/prep/series/" + series.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        StudentPlacementSeriesDetailResponse initial =
                objectMapper.readValue(initialDetail, StudentPlacementSeriesDetailResponse.class);
        assertThat(initial.progressStatus()).isEqualTo(PlacementProgressStatus.NOT_STARTED);
        assertThat(initial.progressPercentage()).isZero();

        // Continue points at the first item (the question) before anything is done.
        String continueBody = mockMvc.perform(get("/api/v1/placement/prep/series/" + series.id() + "/continue")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementContinueResponse pointer = objectMapper.readValue(continueBody, PlacementContinueResponse.class);
        assertThat(pointer.allComplete()).isFalse();
        assertThat(pointer.itemId()).isEqualTo(questionItem.id());

        // Answer the question correctly.
        String answerBody = mockMvc.perform(post(
                        "/api/v1/placement/prep/module-items/" + questionItem.id() + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedOptionIds\": [\"" + optionIdOf(adminToken, question.id(), "Option A") + "\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementModuleItemAnswerResponse answer =
                objectMapper.readValue(answerBody, PlacementModuleItemAnswerResponse.class);
        assertThat(answer.correct()).isTrue();
        assertThat(answer.status()).isEqualTo(PlacementProgressStatus.COMPLETED);

        // Start (and thereby create) the practical attempt for the coding item.
        String practicalDetailBody = mockMvc.perform(
                        post("/api/v1/placement/prep/module-items/" + practicalItem.id() + "/start-practical")
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementModuleItemDetailResponse practicalDetail =
                objectMapper.readValue(practicalDetailBody, PlacementModuleItemDetailResponse.class);
        assertThat(practicalDetail.practicalAttemptId()).isNotNull();
        assertThat(practicalDetail.status()).isEqualTo(PlacementProgressStatus.IN_PROGRESS);

        // Complete the resource item through the existing resource start/complete endpoints.
        mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/start")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Module now has 2 of 3 required items complete (question + resource); practical still open.
        String midDetailBody = mockMvc.perform(get("/api/v1/placement/prep/series/" + series.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        StudentPlacementSeriesDetailResponse midDetail =
                objectMapper.readValue(midDetailBody, StudentPlacementSeriesDetailResponse.class);
        assertThat(midDetail.progressStatus()).isEqualTo(PlacementProgressStatus.IN_PROGRESS);
        StudentPlacementModuleResponse midModule = midDetail.modules().get(0);
        assertThat(midModule.completedItemCount()).isEqualTo(2);
        assertThat(midModule.status()).isEqualTo(PlacementProgressStatus.IN_PROGRESS);

        // Resource item's status inside the module response reflects the resource system live.
        assertThat(midModule.items()).filteredOn(i -> i.id().equals(resourceItem.id()))
                .extracting(StudentPlacementModuleItemResponse::status)
                .containsExactly(PlacementProgressStatus.COMPLETED);
    }

    private UUID optionIdOf(String adminToken, UUID questionId, String optionText) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/questions/" + questionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.studen.questionbank.QuestionResponse question =
                objectMapper.readValue(body, com.studen.questionbank.QuestionResponse.class);
        return question.options().stream().filter(o -> o.optionText().equals(optionText)).findFirst().orElseThrow().id();
    }

    // --- Access control --------------------------------------------------------------------------

    @Test
    void answeringAnItemInAnUnpublishedSeries_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prep-unpub-admin@example.com");
        String studentToken = registerAndGetToken("pl-prep-unpub-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Unpublished Role");
        UUID skillId = createSkill(adminToken, "Unpublished Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Unpublished question?");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Unpublished Series", null, null);
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Unpublished Module");
        PlacementModuleItemResponse item = addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.QUESTION, question.id(), null, null, 0, true));
        // Deliberately never published.

        mockMvc.perform(get("/api/v1/placement/prep/module-items/" + item.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSeries_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/placement/prep/series"))
                .andExpect(status().isUnauthorized());
    }
}
