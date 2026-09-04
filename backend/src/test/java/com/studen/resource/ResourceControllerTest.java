package com.studen.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
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

    private ResourceDetailResponse createAndPublishPdfResource(String adminToken, UUID skillId, String title) throws Exception {
        ResourceRequest request = new ResourceRequest(title, "desc", ResourceType.PDF, skillId, Difficulty.EASY,
                10, null, null, List.of());
        String createdBody = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(createdBody, ResourceDetailResponse.class).id();

        byte[] pdfBytes = "%PDF-1.4\n%fake pdf content for tests\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "My Notes.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/v1/admin/resources/" + id + "/upload-file")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/resources/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return objectMapper.readValue(createdBody, ResourceDetailResponse.class);
    }

    // --- PDF file serving (view must be inline, download must be attachment) -------------------

    @Test
    void viewFile_publishedPdfResource_returnsInlinePdfWithCorrectHeaders() throws Exception {
        String adminToken = registerAdminAndGetToken("res-file-view-admin@example.com");
        String studentToken = registerAndGetToken("res-file-view-student@example.com");
        UUID skillId = createSkill(adminToken, "Res File View Skill");
        ResourceDetailResponse resource = createAndPublishPdfResource(adminToken, skillId, "Viewable PDF");

        mockMvc.perform(get("/api/v1/resources/" + resource.id() + "/file")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"My Notes.pdf\""));
    }

    @Test
    void downloadFile_publishedPdfResource_returnsAttachmentPdfWithCorrectHeaders() throws Exception {
        String adminToken = registerAdminAndGetToken("res-file-dl-admin@example.com");
        String studentToken = registerAndGetToken("res-file-dl-student@example.com");
        UUID skillId = createSkill(adminToken, "Res File Download Skill");
        ResourceDetailResponse resource = createAndPublishPdfResource(adminToken, skillId, "Downloadable PDF");

        mockMvc.perform(get("/api/v1/resources/" + resource.id() + "/file/download")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"My Notes.pdf\""));
    }

    @Test
    void viewFile_draftResource_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("res-file-draft-admin@example.com");
        String studentToken = registerAndGetToken("res-file-draft-student@example.com");
        UUID skillId = createSkill(adminToken, "Res File Draft Skill");
        ResourceRequest request = new ResourceRequest("Draft PDF", "desc", ResourceType.PDF, skillId, Difficulty.EASY,
                10, null, null, List.of());
        String createdBody = mockMvc.perform(post("/api/v1/admin/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(createdBody, ResourceDetailResponse.class).id();

        mockMvc.perform(get("/api/v1/resources/" + id + "/file")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewFile_resourceWithoutUploadedFile_returnsNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("res-file-nofile-admin@example.com");
        String studentToken = registerAndGetToken("res-file-nofile-student@example.com");
        UUID skillId = createSkill(adminToken, "Res File No File Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "No File Resource");

        mockMvc.perform(get("/api/v1/resources/" + resource.id() + "/file")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
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

        // Assessment generation requires app.assessment.default-question-count (30) published
        // questions to be available for the skill — see AssessmentProperties.
        publishQuestionsWithTag(adminToken, skillId, "Weak", 30, "res-ml-weak-tag");

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

    // --- TagParser-based topic matching: a composite weak tag (language-topic1-topic2-topic3)
    // must match a resource tagged with just one of its topics, or with the bare language alone,
    // without requiring the exact composite string (see com.studen.common.tag.TagParser). --------

    @Test
    void myLearning_compositeWeakTag_matchesByTopicAndLanguageAboveUnrelatedAndNoTagResources() throws Exception {
        String adminToken = registerAdminAndGetToken("res-ml-topic-admin@example.com");
        String studentToken = registerAndGetToken("res-ml-topic-student@example.com");
        UUID skillId = createSkill(adminToken, "Res ML Topic Skill");

        // Composite tag: language "restopic", topics [lists, loops, references].
        publishQuestionsWithTag(adminToken, skillId, "Weak", 30, "restopic-lists-loops-references");

        ResourceDetailResponse topicMatch = createAndPublishResource(adminToken, skillId, "Topic Match Resource",
                "restopic-lists");
        ResourceDetailResponse languageOnly = createAndPublishResource(adminToken, skillId, "Language Only Resource",
                "restopic");
        ResourceDetailResponse unrelatedTag = createAndPublishResource(adminToken, skillId, "Unrelated Tag Resource",
                "restopic-unrelatedtopic");
        ResourceDetailResponse noTags = createAndPublishResource(adminToken, skillId, "No Tag Resource");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);

        assertThat(response.groups()).hasSize(1);
        WeakAreaGroupResponse group = response.groups().get(0);
        assertThat(group.weakTags()).containsExactly("restopic-lists-loops-references");

        // Once real topic signal exists (lists/loops/references), a resource carrying its own
        // different, specific topic (unrelatedTag, tagged "restopic-unrelatedtopic") is excluded
        // outright, not merely ranked last — showing it as a recommendation for this weakness would
        // be actively misleading. A fully untagged resource makes no contradicting claim and still
        // fills in as a harmless fallback.
        List<UUID> resourceIds = group.resources().stream().map(ResourceCardResponse::id).toList();
        assertThat(resourceIds).containsExactly(topicMatch.id(), languageOnly.id(), noTags.id());
        assertThat(resourceIds).doesNotContain(unrelatedTag.id());
    }

    // --- Bug report: multiple weak tags on one skill must ALL surface as distinct Focus Area
    // topics (never collapsed/hidden), resources with their own unrelated specific topic must never
    // appear just because they share a skill, and duplicate topics across weak tags must not
    // duplicate a resource. -------------------------------------------------------------------------

    @Test
    void myLearning_multipleWeakTagsOnOneSkill_allTopicsSurfaceAndUnrelatedResourcesExcluded() throws Exception {
        String adminToken = registerAdminAndGetToken("res-ml-multi-admin@example.com");
        String studentToken = registerAndGetToken("res-ml-multi-student@example.com");
        UUID skillId = createSkill(adminToken, "Res ML Multi Skill");

        // Six distinct weak tags on one skill, exactly mirroring the bug report's scenario. Total is
        // exactly the default assessment question count (30) so every published question is
        // guaranteed selected — no random-selection flakiness around tag coverage.
        publishQuestionsWithTag(adminToken, skillId, "Q", 6, "python-lists");
        publishQuestionsWithTag(adminToken, skillId, "Q", 6, "python-dictionaries");
        publishQuestionsWithTag(adminToken, skillId, "Q", 5, "python-dictionaries-loops");
        publishQuestionsWithTag(adminToken, skillId, "Q", 5, "python-functions");
        publishQuestionsWithTag(adminToken, skillId, "Q", 4, "python-functions-default-arguments");
        publishQuestionsWithTag(adminToken, skillId, "Q", 4, "python-loops-lists");

        // Only "lists" and "loops" have a matching published resource — "dictionaries",
        // "functions", "default", "arguments" have none yet, and must still appear in Focus Areas.
        createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");
        createAndPublishResource(adminToken, skillId, "Python Loops", "python-loops");
        // Unrelated resources with their own specific, non-weak topics — must NEVER be recommended.
        createAndPublishResource(adminToken, skillId, "Python Variables", "python-variables");
        createAndPublishResource(adminToken, skillId, "Python Syntax", "python-syntax");
        createAndPublishResource(adminToken, skillId, "Python Basics", "python-basics");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);

        assertThat(response.groups()).hasSize(1);
        WeakAreaGroupResponse group = response.groups().get(0);
        assertThat(group.weakTags()).containsExactlyInAnyOrder("python-lists", "python-dictionaries",
                "python-dictionaries-loops", "python-functions", "python-functions-default-arguments",
                "python-loops-lists");

        // ALL six distinct topics must surface — including the four with zero matching resources —
        // never silently dropped, and "python-dictionaries-loops"/"python-loops-lists" must not
        // duplicate "dictionaries"/"loops"/"lists" as separate entries (Set-based dedup).
        List<String> topics = group.topics().stream().map(FocusAreaTopicResponse::topic).toList();
        assertThat(topics).containsExactlyInAnyOrder("lists", "dictionaries", "loops", "functions", "default",
                "arguments");

        // "Weak Skills" overview must reflect the 6 distinct topics, not the 1 skill.
        assertThat(response.overview().weakSkillsCount()).isEqualTo(6);

        // Topics with a matching resource report it; topics with none report an honest 0/0.
        FocusAreaTopicResponse listsTopic = group.topics().stream().filter(t -> t.topic().equals("lists")).findFirst().orElseThrow();
        assertThat(listsTopic.totalCount()).isEqualTo(1);
        FocusAreaTopicResponse dictionariesTopic = group.topics().stream().filter(t -> t.topic().equals("dictionaries")).findFirst().orElseThrow();
        assertThat(dictionariesTopic.totalCount()).isEqualTo(0);

        // Recommended resources: only the genuinely topic-matching ones, never the unrelated ones —
        // this is the core "generic Python resources" bug.
        List<String> titles = group.resources().stream().map(ResourceCardResponse::title).toList();
        assertThat(titles).containsExactlyInAnyOrder("Python Lists", "Python Loops");
        assertThat(titles).doesNotContain("Python Variables", "Python Syntax", "Python Basics");
    }

    @Test
    void myLearning_duplicateTopicAcrossWeakTags_resourceCountedOnceNotTwice() throws Exception {
        String adminToken = registerAdminAndGetToken("res-ml-dup-admin@example.com");
        String studentToken = registerAndGetToken("res-ml-dup-student@example.com");
        UUID skillId = createSkill(adminToken, "Res ML Dup Skill");

        // "lists" appears in both weak tags — must collapse to one Focus Area topic, and the
        // matching resource must appear exactly once in the recommended list.
        publishQuestionsWithTag(adminToken, skillId, "Q", 15, "python-lists");
        publishQuestionsWithTag(adminToken, skillId, "Q", 15, "python-loops-lists");

        ResourceDetailResponse listsRes = createAndPublishResource(adminToken, skillId, "Python Lists Guide", "python-lists");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);

        WeakAreaGroupResponse group = response.groups().get(0);
        List<String> topics = group.topics().stream().map(FocusAreaTopicResponse::topic).toList();
        assertThat(topics).containsExactlyInAnyOrder("lists", "loops");

        List<UUID> resourceIds = group.resources().stream().map(ResourceCardResponse::id).toList();
        assertThat(resourceIds).containsOnlyOnce(listsRes.id());
    }

    @Test
    void myLearning_focusAreasNeverEmptyWhenWeaknessExists_evenWithNoParseableTopic() throws Exception {
        String adminToken = registerAdminAndGetToken("res-ml-fallback-admin@example.com");
        String studentToken = registerAndGetToken("res-ml-fallback-student@example.com");
        UUID skillId = createSkill(adminToken, "Res ML Fallback Skill");

        // A single-segment weak "tag" (no hyphen) parses to zero topics via TagParser — e.g. a
        // topic-fallback-tier bucket name. Focus Areas must still show something, never the
        // "No specific weak topics identified yet" empty state, since a real weakness exists.
        publishQuestionsWithTag(adminToken, skillId, "Q", 30, "General");
        createAndPublishResource(adminToken, skillId, "Some Resource", "python-basics");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);

        WeakAreaGroupResponse group = response.groups().get(0);
        assertThat(group.topics()).isNotEmpty();
        assertThat(response.overview().weakSkillsCount()).isGreaterThan(0);
    }

    // --- My Learning "Focus Areas": composite weak tag broken into individual topics, each with a
    // real completed/total count against the *uncapped* resource set (not the top-N recommended
    // list), plus the top-level overview aggregate. ---

    @Test
    void myLearning_focusAreaTopics_breakDownByTopicWithRealCountsAndOverview() throws Exception {
        String adminToken = registerAdminAndGetToken("res-ml-focus-admin@example.com");
        String studentToken = registerAndGetToken("res-ml-focus-student@example.com");
        UUID skillId = createSkill(adminToken, "Res ML Focus Skill");

        // Composite weak tag: language "focusskill", topics [lists, loops].
        publishQuestionsWithTag(adminToken, skillId, "Weak", 30, "focusskill-lists-loops");

        ResourceDetailResponse listsA = createAndPublishResource(adminToken, skillId, "Lists Guide A", "focusskill-lists");
        ResourceDetailResponse listsB = createAndPublishResource(adminToken, skillId, "Lists Guide B", "focusskill-lists");
        ResourceDetailResponse loopsA = createAndPublishResource(adminToken, skillId, "Loops Guide A", "focusskill-loops");
        // Unrelated topic under the same skill/language — must never be counted into lists/loops,
        // and (since real topic signal exists) must be excluded from the recommended list entirely.
        ResourceDetailResponse dictionariesGuide = createAndPublishResource(adminToken, skillId, "Dictionaries Guide",
                "focusskill-dictionaries");

        createPortfolio(studentToken, Set.of(skillId));
        AssessmentDetailResponse assessment = startAssessment(studentToken, skillId);
        answerAllWrong(studentToken, assessment);
        submit(studentToken, assessment.id());

        // Complete one of the two "lists" resources before reading My Learning.
        mockMvc.perform(post("/api/v1/resources/" + listsA.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/resources/my-learning")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MyLearningResponse response = objectMapper.readValue(body, MyLearningResponse.class);

        WeakAreaGroupResponse group = response.groups().get(0);
        List<FocusAreaTopicResponse> topics = group.topics();
        assertThat(topics).extracting(FocusAreaTopicResponse::topic).containsExactlyInAnyOrder("lists", "loops");

        FocusAreaTopicResponse lists = topics.stream().filter(t -> t.topic().equals("lists")).findFirst().orElseThrow();
        assertThat(lists.totalCount()).isEqualTo(2);
        assertThat(lists.completedCount()).isEqualTo(1);

        FocusAreaTopicResponse loops = topics.stream().filter(t -> t.topic().equals("loops")).findFirst().orElseThrow();
        assertThat(loops.totalCount()).isEqualTo(1);
        assertThat(loops.completedCount()).isEqualTo(0);

        LearningOverviewResponse overview = response.overview();
        // Two distinct weak topics (lists, loops) — "Weak Skills" now counts topics, not skills.
        assertThat(overview.weakSkillsCount()).isEqualTo(2);
        assertThat(overview.assessmentsCompletedCount()).isEqualTo(1);
        assertThat(overview.totalResourceCount()).isEqualTo(group.totalCount());
        assertThat(overview.completedResourceCount()).isEqualTo(group.completedCount());

        List<UUID> ids = group.resources().stream().map(ResourceCardResponse::id).toList();
        assertThat(ids).contains(listsA.id(), listsB.id(), loopsA.id());
        assertThat(ids).doesNotContain(dictionariesGuide.id());
    }
}
