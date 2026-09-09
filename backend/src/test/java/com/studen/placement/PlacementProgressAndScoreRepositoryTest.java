package com.studen.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionResponse;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.time.Instant;
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
 * The three Phase 0 entities that intentionally have no HTTP surface yet — attempts, skill scores
 * and student progress — exercised at the persistence layer, plus the delete guards that stop
 * placement content from being removed out from under student progress.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class PlacementProgressAndScoreRepositoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private PlacementAssessmentRepository assessmentRepository;

    @Autowired
    private PlacementAttemptRepository attemptRepository;

    @Autowired
    private PlacementSkillScoreRepository skillScoreRepository;

    @Autowired
    private PlacementSeriesRepository seriesRepository;

    @Autowired
    private PlacementModuleItemRepository moduleItemRepository;

    @Autowired
    private StudentSeriesProgressRepository seriesProgressRepository;

    @Autowired
    private StudentModuleItemProgressRepository itemProgressRepository;

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

    private User userByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow();
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

    private QuestionResponse createPublishedQuestion(String adminToken, UUID skillId, String text) throws Exception {
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "EASY",
                  "explanation": "Because A is correct.",
                  "tag": "placement-progress-test",
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

    private PlacementAssessmentDetailResponse createAssessment(String adminToken, UUID roleId, String title)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementAssessmentRequest(
                                title, null, roleId, Difficulty.MEDIUM, 30, 50))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementAssessmentDetailResponse.class);
    }

    private PlacementSeriesDetailResponse createSeries(String adminToken, UUID roleId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementSeriesRequest(name, null, null, roleId,
                                null, Difficulty.EASY, null, null, null, null))))
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

    private PlacementModuleItemResponse addQuestionItem(String adminToken, UUID moduleId, UUID questionId)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/placement/series/modules/" + moduleId + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlacementModuleItemRequest(
                                ModuleItemType.QUESTION, questionId, null, null, 0, true))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PlacementModuleItemResponse.class);
    }

    // --- Attempt -> Skill Score ---------------------------------------------------------------

    @Test
    void attemptAndSkillScores_persistAndReadBackNewestFirst() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prog-score-admin@example.com");
        String studentEmail = "pl-prog-score-student@example.com";
        registerAndGetToken(studentEmail);

        PlacementRoleDetailResponse role = createRole(adminToken, "Score Role");
        UUID skillId = createSkill(adminToken, "Score Skill");
        PlacementAssessmentDetailResponse assessment = createAssessment(adminToken, role.id(), "Score Assessment");

        User student = userByEmail(studentEmail);
        PlacementAssessment assessmentEntity = assessmentRepository.findById(assessment.id()).orElseThrow();
        Skill skill = skillRepository.findById(skillId).orElseThrow();

        PlacementAttempt attempt =
                new PlacementAttempt(student, assessmentEntity, Instant.now().minusSeconds(600));
        attempt.setStatus(PlacementAttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attempt.setScore(14);
        attempt.setMaxScore(20);
        attempt.setScorePercentage(70);
        attempt = attemptRepository.save(attempt);

        PlacementSkillScore older =
                new PlacementSkillScore(student, skill, attempt, 45, Instant.now().minusSeconds(3600));
        older.setCorrectCount(9);
        older.setTotalQuestions(20);
        PlacementSkillScore newer = new PlacementSkillScore(student, skill, attempt, 70, Instant.now());
        newer.setCorrectCount(14);
        newer.setTotalQuestions(20);
        skillScoreRepository.saveAll(List.of(older, newer));
        skillScoreRepository.flush();

        List<PlacementAttempt> attempts =
                attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getScorePercentage()).isEqualTo(70);
        assertThat(attempts.get(0).getPlacementAssessment().getId()).isEqualTo(assessment.id());

        // A history, newest first — so the head of the list is the current score for that skill.
        List<PlacementSkillScore> history = skillScoreRepository
                .findByStudentIdAndSkillIdOrderByRecordedAtDesc(student.getId(), skillId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getScorePercentage()).isEqualTo(70);
        assertThat(history.get(1).getScorePercentage()).isEqualTo(45);
        assertThat(history.get(0).getAttempt().getId()).isEqualTo(attempt.getId());
    }

    @Test
    void deletingAnAssessmentWithAttempts_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prog-attempt-guard-admin@example.com");
        String studentEmail = "pl-prog-attempt-guard-student@example.com";
        registerAndGetToken(studentEmail);

        PlacementRoleDetailResponse role = createRole(adminToken, "Attempt Guard Role");
        PlacementAssessmentDetailResponse assessment =
                createAssessment(adminToken, role.id(), "Attempt Guard Assessment");
        PlacementAssessment entity = assessmentRepository.findById(assessment.id()).orElseThrow();

        attemptRepository.saveAndFlush(
                new PlacementAttempt(userByEmail(studentEmail), entity, Instant.now()));

        mockMvc.perform(delete("/api/v1/admin/placement/assessments/" + assessment.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- Student progress ---------------------------------------------------------------------

    @Test
    void studentProgress_spansSeriesModuleAndItem() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prog-progress-admin@example.com");
        String studentEmail = "pl-prog-progress-student@example.com";
        registerAndGetToken(studentEmail);

        PlacementRoleDetailResponse role = createRole(adminToken, "Progress Role");
        UUID skillId = createSkill(adminToken, "Progress Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Progress question?");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Progress Series");
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Progress Module");
        PlacementModuleItemResponse item = addQuestionItem(adminToken, module.id(), question.id());

        User student = userByEmail(studentEmail);
        PlacementSeries seriesEntity = seriesRepository.findById(series.id()).orElseThrow();
        PlacementModuleItem itemEntity = moduleItemRepository.findById(item.id()).orElseThrow();

        StudentSeriesProgress seriesProgress =
                new StudentSeriesProgress(student, seriesEntity, PlacementProgressStatus.IN_PROGRESS);
        seriesProgress.setStartedAt(Instant.now());
        seriesProgressRepository.saveAndFlush(seriesProgress);

        StudentModuleItemProgress itemProgress =
                new StudentModuleItemProgress(student, itemEntity, PlacementProgressStatus.COMPLETED);
        itemProgress.setStartedAt(Instant.now().minusSeconds(120));
        itemProgress.setCompletedAt(Instant.now());
        itemProgressRepository.saveAndFlush(itemProgress);

        assertThat(seriesProgressRepository.findByStudentIdAndSeriesId(student.getId(), series.id()))
                .isPresent();

        StudentModuleItemProgress loaded = itemProgressRepository
                .findByStudentIdAndModuleItemId(student.getId(), item.id()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(PlacementProgressStatus.COMPLETED);
        // Item -> Module -> Series is walkable, which is what makes one item table cover the whole
        // Series -> Modules -> activities progression.
        assertThat(loaded.getModuleItem().getModule().getId()).isEqualTo(module.id());
        assertThat(loaded.getModuleItem().getModule().getSeries().getId()).isEqualTo(series.id());

        assertThat(itemProgressRepository.existsByModuleId(module.id())).isTrue();
        assertThat(itemProgressRepository.existsBySeriesId(series.id())).isTrue();
    }

    @Test
    void studentProgress_isUniquePerStudentAndItem() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prog-unique-admin@example.com");
        String studentEmail = "pl-prog-unique-student@example.com";
        registerAndGetToken(studentEmail);

        PlacementRoleDetailResponse role = createRole(adminToken, "Unique Progress Role");
        UUID skillId = createSkill(adminToken, "Unique Progress Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Unique progress question?");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Unique Progress Series");
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Unique Progress Module");
        PlacementModuleItemResponse item = addQuestionItem(adminToken, module.id(), question.id());

        User student = userByEmail(studentEmail);
        PlacementModuleItem itemEntity = moduleItemRepository.findById(item.id()).orElseThrow();

        itemProgressRepository.saveAndFlush(
                new StudentModuleItemProgress(student, itemEntity, PlacementProgressStatus.IN_PROGRESS));

        assertThatThrownBy(() -> itemProgressRepository.saveAndFlush(
                new StudentModuleItemProgress(student, itemEntity, PlacementProgressStatus.COMPLETED)))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deletingContentAStudentHasStarted_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("pl-prog-delete-guard-admin@example.com");
        String studentEmail = "pl-prog-delete-guard-student@example.com";
        registerAndGetToken(studentEmail);

        PlacementRoleDetailResponse role = createRole(adminToken, "Delete Guard Role");
        UUID skillId = createSkill(adminToken, "Delete Guard Skill");
        QuestionResponse question = createPublishedQuestion(adminToken, skillId, "Delete guard question?");
        PlacementSeriesDetailResponse series = createSeries(adminToken, role.id(), "Delete Guard Series");
        PlacementModuleResponse module = createModule(adminToken, series.id(), "Delete Guard Module");
        PlacementModuleItemResponse item = addQuestionItem(adminToken, module.id(), question.id());

        itemProgressRepository.saveAndFlush(new StudentModuleItemProgress(
                userByEmail(studentEmail),
                moduleItemRepository.findById(item.id()).orElseThrow(),
                PlacementProgressStatus.IN_PROGRESS));

        mockMvc.perform(delete("/api/v1/admin/placement/series/modules/items/" + item.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/admin/placement/series/modules/" + module.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/admin/placement/series/" + series.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
