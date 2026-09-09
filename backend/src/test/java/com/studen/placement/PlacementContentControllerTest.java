package com.studen.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Assessment -> Questions and PlacementSeries -> Modules -> Items, including that a module item
 * links existing Question Bank / practical / resource content rather than duplicating it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementContentControllerTest {

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

    /** Creates a Question Bank MCQ and takes it all the way to PUBLISHED. */
    private QuestionResponse createPublishedQuestion(String adminToken, UUID skillId, String text) throws Exception {
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "explanation": "Because A is correct.",
                  "tag": "placement-foundation-test",
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

    private QuestionResponse createDraftQuestion(String adminToken, UUID skillId, String text) throws Exception {
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "explanation": "Still a draft.",
                  "tag": "placement-foundation-draft",
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
        return objectMapper.readValue(body, QuestionResponse.class);
    }

    private ResourceDetailResponse createPublishedResource(String adminToken, UUID skillId, String title)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResourceRequest(title, "Reference material",
                                ResourceType.EXTERNAL_LINK, skillId, Difficulty.EASY, 20,
                                "https://example.com/placement", null, List.of("placement-foundation-test")))))
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

    private PlacementAssessmentDetailResponse createAssessment(String adminToken, UUID roleId, String title)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAssessmentRequest(
                                title, "Readiness assessment", roleId, Difficulty.MEDIUM, 45, 60))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementAssessmentDetailResponse.class);
    }

    private PlacementSeriesDetailResponse createSeries(String adminToken, UUID roleId, String name,
            List<UUID> skillIds) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementSeriesRequest(name, "Prep series", null,
                                roleId, CompanyType.SERVICE_BASED, Difficulty.MEDIUM, 20, null, skillIds, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementSeriesDetailResponse.class);
    }

    private PlacementModuleResponse createModule(String adminToken, UUID seriesId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series/" + seriesId + "/modules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementModuleRequest(name, null, null, null))))
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

    // --- Authorization ------------------------------------------------------------------------

    @Test
    void createAssessment_asStudent_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-auth-admin@example.com");
        String studentToken = registerAndGetToken("pl-content-auth-student@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Auth Content Role");

        mockMvc.perform(post("/api/v1/admin/placement/assessments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAssessmentRequest(
                                "Nope", null, role.id(), Difficulty.EASY, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSeries_asStudent_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-series-auth-admin@example.com");
        String studentToken = registerAndGetToken("pl-content-series-auth@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Auth Series Role");

        mockMvc.perform(post("/api/v1/admin/placement/series")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementSeriesRequest("Nope", null, null,
                                role.id(), null, Difficulty.EASY, null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    // --- Assessment -> Questions --------------------------------------------------------------

    @Test
    void assessment_addQuestionsAndReadBack() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-assess@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Assessment Role");
        UUID skillId = createSkill(adminToken, "Assessment Content Skill");
        QuestionResponse q1 = createPublishedQuestion(adminToken, skillId, "First readiness question?");
        QuestionResponse q2 = createPublishedQuestion(adminToken, skillId, "Second readiness question?");

        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "SDE Readiness");
        assertThat(assessment.status()).isEqualTo(PlacementContentStatus.DRAFT);
        assertThat(assessment.questions()).isEmpty();

        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(q1.id(), null, 0, 2))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(q2.id(), null, 1, 1))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v1/admin/placement/assessments/" + assessment.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementAssessmentDetailResponse loaded =
                objectMapper.readValue(body, PlacementAssessmentDetailResponse.class);
        assertThat(loaded.questions()).hasSize(2);
        assertThat(loaded.questions().get(0).questionId()).isEqualTo(q1.id());
        assertThat(loaded.questions().get(0).points()).isEqualTo(2);
        assertThat(loaded.questions().get(0).skillId()).isEqualTo(skillId);
    }

    @Test
    void assessment_addingTheSameQuestionTwice_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-assess-dupe@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Duplicate Question Role");
        UUID skillId = createSkill(adminToken, "Duplicate Question Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Only once please?");
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "Dedupe Assessment");

        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(question.id(), null, 0, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(question.id(), null, 1, 1))))
                .andExpect(status().isConflict());
    }

    @Test
    void assessment_addingADraftQuestion_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-assess-draft@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Draft Question Role");
        UUID skillId = createSkill(adminToken, "Draft Question Skill");
        QuestionResponse draft = createDraftQuestion(adminToken, skillId, "Not published yet?");
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "Draft Guard");

        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(draft.id(), null, 0, 1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assessment_publishWithNoQuestions_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-assess-empty@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Empty Assessment Role");
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "Empty Assessment");

        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assessment_removingAQuestion_leavesTheQuestionBankRowIntact() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-assess-remove@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Remove Question Role");
        UUID skillId = createSkill(adminToken, "Remove Question Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Detachable question?");
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "Detach Assessment");

        mockMvc.perform(post("/api/v1/admin/placement/assessments/" + assessment.id() + "/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlacementAssessmentQuestionRequest(question.id(), null, 0, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/admin/placement/assessments/" + assessment.id()
                        + "/questions/" + question.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/questions/" + question.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // --- Series -> Modules -> Items -----------------------------------------------------------

    @Test
    void series_withModulesAndMixedItems_readsBackInOrder() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-series@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Series Content Role");
        UUID skillId = createSkill(adminToken, "Series Content Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Series MCQ question?");
        ResourceDetailResponse resource = createPublishedResource(adminToken, skillId, "Series Reading");
        PracticalAssessmentDetailResponse practical =
                createPublishedPractical(adminToken, skillId, "Series Coding Task");

        PlacementSeriesDetailResponse series =
                createSeries(adminToken, role.id(), "Company Prep Series", List.of(skillId));
        assertThat(series.status()).isEqualTo(PlacementContentStatus.DRAFT);
        assertThat(series.skillsCovered()).extracting(SkillResponse::id).containsExactly(skillId);

        PlacementModuleResponse module = createModule(adminToken, series.id(), "Programming Fundamentals");

        addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.RESOURCE, null, null, resource.id(), 0, true));
        addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.QUESTION, question.id(), null, null, 1, true));
        addItem(adminToken, module.id(), new PlacementModuleItemRequest(
                ModuleItemType.PRACTICAL_ASSESSMENT, null, practical.id(), null, 2, false));

        String body = mockMvc.perform(get("/api/v1/admin/placement/series/" + series.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PlacementSeriesDetailResponse loaded =
                objectMapper.readValue(body, PlacementSeriesDetailResponse.class);

        assertThat(loaded.modules()).hasSize(1);
        List<PlacementModuleItemResponse> items = loaded.modules().get(0).items();
        assertThat(items).extracting(PlacementModuleItemResponse::itemType)
                .containsExactly(ModuleItemType.RESOURCE, ModuleItemType.QUESTION,
                        ModuleItemType.PRACTICAL_ASSESSMENT);
        assertThat(items).extracting(PlacementModuleItemResponse::targetId)
                .containsExactly(resource.id(), question.id(), practical.id());
        assertThat(items.get(2).required()).isFalse();
    }

    @Test
    void moduleItem_withMismatchedTypeAndId_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-item-mismatch@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Mismatch Role");
        UUID skillId = createSkill(adminToken, "Mismatch Skill");
        ResourceDetailResponse resource = createPublishedResource(adminToken, skillId, "Mismatch Reading");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Mismatch Series", null);
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Mismatch Module");

        // Declares QUESTION but supplies a resource id.
        mockMvc.perform(post("/api/v1/admin/placement/series/modules/" + module.id() + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementModuleItemRequest(
                                ModuleItemType.QUESTION, null, null, resource.id(), 0, true))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moduleItem_addingTheSameContentTwice_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-item-dupe@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Item Dupe Role");
        UUID skillId = createSkill(adminToken, "Item Dupe Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Item dupe question?");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Item Dupe Series", null);
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Item Dupe Module");

        addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.QUESTION, question.id(), null, null, 0, true));

        mockMvc.perform(post("/api/v1/admin/placement/series/modules/" + module.id() + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementModuleItemRequest(
                                ModuleItemType.QUESTION, question.id(), null, null, 1, true))))
                .andExpect(status().isConflict());
    }

    @Test
    void modules_reorder_rewritesDisplayOrder() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-reorder@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Reorder Role");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Reorder Series", null);
        PlacementModuleResponse first = createModule(adminToken, series.id(), "Module One");
        PlacementModuleResponse second = createModule(adminToken, series.id(), "Module Two");
        PlacementModuleResponse third = createModule(adminToken, series.id(), "Module Three");

        String body = mockMvc.perform(put("/api/v1/admin/placement/series/" + series.id() + "/modules/reorder")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReorderRequest(List.of(third.id(), first.id(), second.id())))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<PlacementModuleResponse> reordered =
                List.of(objectMapper.readValue(body, PlacementModuleResponse[].class));

        assertThat(reordered).extracting(PlacementModuleResponse::id)
                .containsExactly(third.id(), first.id(), second.id());
        assertThat(reordered).extracting(PlacementModuleResponse::displayOrder).containsExactly(0, 1, 2);
    }

    @Test
    void modules_reorderWithAPartialList_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-reorder-partial@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Partial Reorder Role");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Partial Reorder Series", null);
        PlacementModuleResponse first = createModule(adminToken, series.id(), "Partial Module One");
        createModule(adminToken, series.id(), "Partial Module Two");

        mockMvc.perform(put("/api/v1/admin/placement/series/" + series.id() + "/modules/reorder")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReorderRequest(List.of(first.id())))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void series_publishWithNoModules_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-series-empty@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Empty Series Role");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Empty Series", null);

        mockMvc.perform(post("/api/v1/admin/placement/series/" + series.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void series_publishThenArchive_transitionsStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-series-status@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Series Status Role");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Status Series", null);
        createModule(adminToken, series.id(), "Status Module");

        String published = mockMvc.perform(post("/api/v1/admin/placement/series/" + series.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(published, PlacementSeriesDetailResponse.class).status())
                .isEqualTo(PlacementContentStatus.PUBLISHED);

        String archived = mockMvc.perform(post("/api/v1/admin/placement/series/" + series.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(archived, PlacementSeriesDetailResponse.class).status())
                .isEqualTo(PlacementContentStatus.ARCHIVED);
    }

    @Test
    void deletingASeries_leavesTheLinkedContentIntact() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-series-delete@example.com");
        PlacementRoleDetailResponse role = createRole(adminToken, "Deletable Series Role");
        UUID skillId = createSkill(adminToken, "Deletable Series Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Survives series deletion?");
        ResourceDetailResponse resource = createPublishedResource(adminToken, skillId, "Surviving Reading");

        PlacementSeriesDetailResponse series =
                createSeries(adminToken, role.id(), "Deletable Series", List.of(skillId));
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Deletable Module");
        addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.QUESTION, question.id(), null, null, 0, true));
        addItem(adminToken, module.id(),
                new PlacementModuleItemRequest(ModuleItemType.RESOURCE, null, null, resource.id(), 1, true));

        mockMvc.perform(delete("/api/v1/admin/placement/series/" + series.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/questions/" + question.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/resources/" + resource.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // --- Skill -> Learning Resources ----------------------------------------------------------

    @Test
    void skillToLearningResources_isQueryableWithoutANewPlacementTable() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-content-skill-resources@example.com");
        UUID skillId = createSkill(adminToken, "Learning Resource Skill");
        ResourceDetailResponse resource = createPublishedResource(adminToken, skillId, "Skill Linked Reading");

        String body = mockMvc.perform(get("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("skillId", skillId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(resource.id().toString());
    }
}
