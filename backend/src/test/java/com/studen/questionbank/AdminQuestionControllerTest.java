package com.studen.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
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
// See AuthControllerTest for why: AuthRateLimitFilter's per-IP counter is shared (and
// accumulates) across every @SpringBootTest in this JVM run.
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AdminQuestionControllerTest {

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

    /** Registers a fresh user and promotes them to ADMIN directly via the repository — this
     * codebase has no in-app promotion endpoint by design (see AdminBootstrapRunner), so tests
     * simulate the env-var bootstrap by flipping the role the same way. Role is re-read from the
     * DB on every request (UserDetailsServiceImpl), so no new token is needed after promoting. */
    private String registerAdminAndGetToken(String email) throws Exception {
        String token = registerAndGetToken(email);
        userRepository.findByEmail(email).ifPresent(user -> user.setRole(UserRole.ADMIN));
        return token;
    }

    private UUID createSkill(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private String mcqSinglePayload(UUID skillId, String questionText) {
        return """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "explanation": "Because A is correct.",
                  "options": [
                    {"optionText": "Option A", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "Option B", "displayOrder": 1, "isCorrect": false}
                  ]
                }
                """.formatted(skillId, questionText);
    }

    private String mcqMultiplePayload(UUID skillId, String questionText) {
        return """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_MULTIPLE",
                  "difficulty": "MEDIUM",
                  "explanation": "A and C are both correct.",
                  "options": [
                    {"optionText": "Option A", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "Option B", "displayOrder": 1, "isCorrect": false},
                    {"optionText": "Option C", "displayOrder": 2, "isCorrect": true}
                  ]
                }
                """.formatted(skillId, questionText);
    }

    private QuestionResponse createQuestion(String token, String payload) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, QuestionResponse.class);
    }

    private QuestionResponse publishFlow(String adminToken, UUID questionId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/questions/" + questionId + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/v1/admin/questions/" + questionId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, QuestionResponse.class);
    }

    // --- Authorization matrix (spec §17/§56) -------------------------------------------------

    @Test
    void create_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("qb-student-create@example.com");
        String adminToken = registerAdminAndGetToken("qb-owner-for-skill1@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Create Guard");

        mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, "Should be forbidden?")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminMutations_asStudent_allReturn403() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-idor-admin@example.com");
        String studentToken = registerAndGetToken("qb-idor-student@example.com");
        UUID skillId = createSkill(adminToken, "QB IDOR Skill");
        QuestionResponse question = createQuestion(adminToken, mcqSinglePayload(skillId, "IDOR guard question?"));
        UUID id = question.id();

        mockMvc.perform(get("/api/v1/admin/questions/" + id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/questions/" + id)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, "Edited by a student?")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/publish")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/questions/" + id + "/archive")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/questions/" + id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // --- Create / validation --------------------------------------------------------------------

    @Test
    void create_asAdmin_startsAsDraftAndExposesCorrectAnswer() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-create-admin@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Draft");

        QuestionResponse response = createQuestion(adminToken, mcqSinglePayload(skillId, "Which option is correct?"));

        assertThat(response.status()).isEqualTo(QuestionStatus.DRAFT);
        assertThat(response.skillId()).isEqualTo(skillId);
        assertThat(response.skillName()).isEqualTo("QB Skill Draft");
        assertThat(response.options()).hasSize(2);
        assertThat(response.options().stream().filter(QuestionOptionResponse::isCorrect).count()).isEqualTo(1);
    }

    @Test
    void create_mcqMultiple_supportsMultipleCorrectOptions() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-mcq-multi@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Multi");

        QuestionResponse response = createQuestion(adminToken, mcqMultiplePayload(skillId, "Which are valid?"));

        long correctCount = response.options().stream().filter(QuestionOptionResponse::isCorrect).count();
        assertThat(correctCount).isEqualTo(2);
    }

    @Test
    void create_mcqSingleWithTwoCorrectOptions_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-mcq-single-invalid@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Invalid Single");
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "Invalid single with two corrects?",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "options": [
                    {"optionText": "A", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "B", "displayOrder": 1, "isCorrect": true}
                  ]
                }
                """.formatted(skillId);

        mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void create_withBlankQuestionText_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-blank-text@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Blank");
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "",
                  "questionType": "TRUE_FALSE",
                  "difficulty": "EASY",
                  "options": [
                    {"optionText": "True", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "False", "displayOrder": 1, "isCorrect": false}
                  ]
                }
                """.formatted(skillId);

        mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateQuestionText_returnsWarningButStillCreates() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-duplicate@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Duplicate");
        QuestionResponse first = createQuestion(adminToken, mcqSinglePayload(skillId, "Is this a duplicate question?"));

        String secondBody = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, "  Is this a DUPLICATE question?  ")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        QuestionResponse second = objectMapper.readValue(secondBody, QuestionResponse.class);

        assertThat(second.duplicateWarning()).isNotNull();
        assertThat(second.duplicateWarning().existingQuestionId()).isEqualTo(first.id());
    }

    // --- Edit / historical integrity (spec §24/§25/§14) -----------------------------------------

    @Test
    void update_draftQuestion_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-edit-draft@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Edit");
        QuestionResponse created = createQuestion(adminToken, mcqSinglePayload(skillId, "Original text?"));

        mockMvc.perform(patch("/api/v1/admin/questions/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, "Edited text?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value("Edited text?"));
    }

    @Test
    void update_publishedQuestion_returns409AndLeavesContentUnchanged() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-edit-published@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Edit Published");
        QuestionResponse created = createQuestion(adminToken, mcqSinglePayload(skillId, "Immutable once published?"));
        QuestionResponse published = publishFlow(adminToken, created.id());
        assertThat(published.status()).isEqualTo(QuestionStatus.PUBLISHED);

        mockMvc.perform(patch("/api/v1/admin/questions/" + created.id())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, "Trying to sneak an edit in?")))
                .andExpect(status().isConflict());

        String body = mockMvc.perform(get("/api/v1/admin/questions/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        QuestionResponse stillOriginal = objectMapper.readValue(body, QuestionResponse.class);
        assertThat(stillOriginal.questionText()).isEqualTo("Immutable once published?");
    }

    @Test
    void createNewVersion_ofPublishedQuestion_createsDraftAndArchivesOldOnPublish() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-version@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Version");
        QuestionResponse v1 = createQuestion(adminToken, mcqSinglePayload(skillId, "Versioned question v1?"));
        QuestionResponse v1Published = publishFlow(adminToken, v1.id());

        String newVersionBody = mockMvc.perform(post("/api/v1/admin/questions/" + v1.id() + "/new-version")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        QuestionResponse v2Draft = objectMapper.readValue(newVersionBody, QuestionResponse.class);
        assertThat(v2Draft.status()).isEqualTo(QuestionStatus.DRAFT);
        assertThat(v2Draft.version()).isEqualTo(2);
        assertThat(v2Draft.previousVersionId()).isEqualTo(v1.id());

        // The old version stays PUBLISHED until the new one is itself published.
        String v1StillBody = mockMvc.perform(get("/api/v1/admin/questions/" + v1.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(v1StillBody, QuestionResponse.class).status()).isEqualTo(QuestionStatus.PUBLISHED);

        publishFlow(adminToken, v2Draft.id());

        String v1AfterBody = mockMvc.perform(get("/api/v1/admin/questions/" + v1.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(v1AfterBody, QuestionResponse.class).status()).isEqualTo(QuestionStatus.ARCHIVED);
    }

    // --- Delete guard (spec §25) -----------------------------------------------------------------

    @Test
    void delete_untouchedDraft_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-delete-draft@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Delete");
        QuestionResponse created = createQuestion(adminToken, mcqSinglePayload(skillId, "Delete me while draft?"));

        mockMvc.perform(delete("/api/v1/admin/questions/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_questionThatWasSubmittedForReview_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-delete-review@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Delete Review");
        QuestionResponse created = createQuestion(adminToken, mcqSinglePayload(skillId, "Cannot hard-delete after review?"));
        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/questions/" + created.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- Status transitions ------------------------------------------------------------------

    @Test
    void publish_withoutExplanation_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-publish-no-explanation@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill No Explanation");
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "Missing explanation before publish?",
                  "questionType": "TRUE_FALSE",
                  "difficulty": "EASY",
                  "options": [
                    {"optionText": "True", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "False", "displayOrder": 1, "isCorrect": false}
                  ]
                }
                """.formatted(skillId);
        QuestionResponse created = createQuestion(adminToken, payload);

        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_beforeSubmittingForReview_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-publish-order@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Publish Order");
        QuestionResponse created = createQuestion(adminToken, mcqSinglePayload(skillId, "Publish without review first?"));

        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void archive_alreadyArchivedQuestion_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-archive-twice@example.com");
        UUID skillId = createSkill(adminToken, "QB Skill Archive Twice");
        QuestionResponse created = createQuestion(adminToken, mcqSinglePayload(skillId, "Archive me twice?"));
        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- List / filters / pagination / search -------------------------------------------------

    @Test
    void list_filtersBySkillDifficultyTypeAndStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-filters@example.com");
        UUID skillA = createSkill(adminToken, "QB Filter Skill A");
        UUID skillB = createSkill(adminToken, "QB Filter Skill B");
        createQuestion(adminToken, mcqSinglePayload(skillA, "Skill A easy MCQ single?"));
        createQuestion(adminToken, mcqMultiplePayload(skillB, "Skill B medium MCQ multiple?"));

        mockMvc.perform(get("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("skillId", skillA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].skillName").value("QB Filter Skill A"));

        mockMvc.perform(get("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("difficulty", "MEDIUM")
                        .param("type", "MCQ_MULTIPLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.skillName == 'QB Filter Skill B')]").exists());

        mockMvc.perform(get("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "DRAFT")
                        .param("skillId", skillA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_searchMatchesQuestionText() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-search@example.com");
        UUID skillId = createSkill(adminToken, "QB Search Skill");
        createQuestion(adminToken, mcqSinglePayload(skillId, "A very unique searchable phrase about zebras?"));
        createQuestion(adminToken, mcqSinglePayload(skillId, "Something else entirely unrelated?"));

        mockMvc.perform(get("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "zebras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_pagination_respectsPageAndSize() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-pagination@example.com");
        UUID skillId = createSkill(adminToken, "QB Pagination Skill");
        for (int i = 0; i < 5; i++) {
            createQuestion(adminToken, mcqSinglePayload(skillId, "Pagination question number " + i + "?"));
        }

        mockMvc.perform(get("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("skillId", skillId.toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void stats_countsQuestionsByStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-stats@example.com");
        UUID skillId = createSkill(adminToken, "QB Stats Skill");
        QuestionResponse draft = createQuestion(adminToken, mcqSinglePayload(skillId, "Stats draft question?"));
        QuestionResponse toPublish = createQuestion(adminToken, mcqSinglePayload(skillId, "Stats published question?"));
        publishFlow(adminToken, toPublish.id());

        mockMvc.perform(get("/api/v1/admin/questions/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.published").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        assertThat(draft.status()).isEqualTo(QuestionStatus.DRAFT);
    }
}
