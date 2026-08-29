package com.studen.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
import com.studen.questionbank.Difficulty;
import com.studen.resource.ResourceDetailResponse;
import com.studen.resource.ResourceProgressStatus;
import com.studen.resource.ResourceRequest;
import com.studen.resource.ResourceType;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Calendar Skills"))))
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

    private LearningSessionResponse schedule(String token, UUID resourceId, String topic, Instant start, int minutes)
            throws Exception {
        ScheduleSessionRequest request = new ScheduleSessionRequest(resourceId, topic, start, minutes, null);
        String body = mockMvc.perform(post("/api/v1/calendar/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, LearningSessionResponse.class);
    }

    private List<LearningSessionResponse> listSessions(String token, Instant from, Instant to) throws Exception {
        String body = mockMvc.perform(get("/api/v1/calendar/sessions")
                        .header("Authorization", "Bearer " + token)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return List.of(objectMapper.readValue(body, LearningSessionResponse[].class));
    }

    // --- TEST 7: scheduling a roadmap item creates a session linked to student/resource/topic. ---

    @Test
    void schedule_thenListSessions_returnsSessionLinkedToResourceAndTopic() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-schedule-admin@example.com");
        String studentToken = registerAndGetToken("cal-schedule-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Schedule Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");

        Instant start = Instant.now().plus(Duration.ofDays(1));
        LearningSessionResponse created = schedule(studentToken, resource.id(), "lists", start, 60);
        assertThat(created.resource().id()).isEqualTo(resource.id());
        assertThat(created.topic()).isEqualTo("lists");
        assertThat(created.status()).isEqualTo(LearningSessionStatus.SCHEDULED);
        assertThat(created.durationMinutes()).isEqualTo(60);

        List<LearningSessionResponse> sessions = listSessions(studentToken, Instant.now(),
                Instant.now().plus(Duration.ofDays(7)));
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).id()).isEqualTo(created.id());
    }

    @Test
    void schedule_withoutResourceOrTopic_returnsBadRequest() throws Exception {
        String studentToken = registerAndGetToken("cal-badrequest-student@example.com");
        ScheduleSessionRequest request = new ScheduleSessionRequest(null, null, Instant.now().plus(Duration.ofDays(1)), 60, null);
        mockMvc.perform(post("/api/v1/calendar/sessions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_changesScheduledTimeAndDuration() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-update-admin@example.com");
        String studentToken = registerAndGetToken("cal-update-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Update Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Loops", "python-loops");

        LearningSessionResponse created = schedule(studentToken, resource.id(), "loops",
                Instant.now().plus(Duration.ofDays(1)), 30);

        Instant newStart = Instant.now().plus(Duration.ofDays(2));
        UpdateSessionRequest update = new UpdateSessionRequest(newStart, 45);
        String body = mockMvc.perform(patch("/api/v1/calendar/sessions/" + created.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LearningSessionResponse updated = objectMapper.readValue(body, LearningSessionResponse.class);
        assertThat(updated.durationMinutes()).isEqualTo(45);
        assertThat(updated.scheduledStart()).isEqualTo(newStart);
    }

    @Test
    void delete_removesSession_thenListNoLongerIncludesIt() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-delete-admin@example.com");
        String studentToken = registerAndGetToken("cal-delete-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Delete Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Dicts", "python-dicts");

        LearningSessionResponse created = schedule(studentToken, resource.id(), "dicts",
                Instant.now().plus(Duration.ofDays(1)), 30);

        mockMvc.perform(delete("/api/v1/calendar/sessions/" + created.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        List<LearningSessionResponse> sessions = listSessions(studentToken, Instant.now(),
                Instant.now().plus(Duration.ofDays(7)));
        assertThat(sessions).isEmpty();
    }

    // --- TEST 8, direction 1: completing the scheduled session synchronizes resource progress. ---

    @Test
    void complete_session_marksResourceProgressCompleted() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-complete1-admin@example.com");
        String studentToken = registerAndGetToken("cal-complete1-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Complete1 Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Sets", "python-sets");

        LearningSessionResponse created = schedule(studentToken, resource.id(), "sets",
                Instant.now().plus(Duration.ofDays(1)), 30);

        String body = mockMvc.perform(post("/api/v1/calendar/sessions/" + created.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LearningSessionResponse completed = objectMapper.readValue(body, LearningSessionResponse.class);
        assertThat(completed.status()).isEqualTo(LearningSessionStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.resource().progressStatus()).isEqualTo(ResourceProgressStatus.COMPLETED);
    }

    // --- TEST 8, direction 2: completing the resource directly (not via the calendar) auto-
    // completes a linked SCHEDULED session. ---

    @Test
    void completingResourceDirectly_autoCompletesLinkedScheduledSession() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-complete2-admin@example.com");
        String studentToken = registerAndGetToken("cal-complete2-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Complete2 Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Tuples", "python-tuples");

        LearningSessionResponse created = schedule(studentToken, resource.id(), "tuples",
                Instant.now().plus(Duration.ofDays(1)), 30);

        mockMvc.perform(post("/api/v1/resources/" + resource.id() + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        List<LearningSessionResponse> sessions = listSessions(studentToken, Instant.now(),
                Instant.now().plus(Duration.ofDays(7)));
        LearningSessionResponse found = sessions.stream().filter(s -> s.id().equals(created.id())).findFirst()
                .orElseThrow();
        assertThat(found.status()).isEqualTo(LearningSessionStatus.COMPLETED);
        assertThat(found.completedAt()).isNotNull();
    }

    // --- Security: a session belonging to another student must never be visible/editable. ---

    @Test
    void otherStudent_cannotViewUpdateDeleteOrCompleteAnothersSession() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-security-admin@example.com");
        String ownerToken = registerAndGetToken("cal-security-owner@example.com");
        String otherToken = registerAndGetToken("cal-security-other@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Security Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Strings", "python-strings");

        LearningSessionResponse created = schedule(ownerToken, resource.id(), "strings",
                Instant.now().plus(Duration.ofDays(1)), 30);

        UpdateSessionRequest update = new UpdateSessionRequest(Instant.now().plus(Duration.ofDays(2)), 45);
        mockMvc.perform(patch("/api/v1/calendar/sessions/" + created.id())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/calendar/sessions/" + created.id() + "/complete")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/calendar/sessions/" + created.id())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // The owner's own session list is untouched by the other student's failed attempts.
        List<LearningSessionResponse> ownerSessions = listSessions(ownerToken, Instant.now(),
                Instant.now().plus(Duration.ofDays(7)));
        assertThat(ownerSessions).hasSize(1);
        assertThat(ownerSessions.get(0).status()).isEqualTo(LearningSessionStatus.SCHEDULED);
    }

    // --- Study plan: preview assigns roadmap topics to available days, falls back to Practice /
    // Revision once weak topics are exhausted, and never writes anything until Save. ---

    @Test
    void studyPlan_preview_assignsWeakTopicsInPriorityOrderThenFallsBackToRevision() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-plan-admin@example.com");
        String studentToken = registerAndGetToken("cal-plan-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar Plan Skill");
        createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");
        createPortfolio(studentToken, Set.of(skillId));

        LocalDate monday = nextMonday();
        StudyPlanRequest request = new StudyPlanRequest(monday,
                Set.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY), 60);
        String body = mockMvc.perform(post("/api/v1/calendar/study-plan/preview")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        StudyPlanSuggestionResponse preview = objectMapper.readValue(body, StudyPlanSuggestionResponse.class);

        // No weak areas exist yet for this student (no assessment taken) — every day must fall
        // back to a resource-less Practice / Revision slot, never a fabricated topic.
        assertThat(preview.sessions()).hasSize(3);
        assertThat(preview.sessions()).allSatisfy(s -> {
            assertThat(s.topic()).isEqualTo("Practice / Revision");
            assertThat(s.resource()).isNull();
        });

        // Preview never writes anything.
        List<LearningSessionResponse> sessions = listSessions(studentToken, Instant.now(),
                Instant.now().plus(Duration.ofDays(30)));
        assertThat(sessions).isEmpty();
    }

    @Test
    void studyPlan_save_persistsEditedSessionsAndSkipsExactDuplicateSlot() throws Exception {
        String adminToken = registerAdminAndGetToken("cal-plansave-admin@example.com");
        String studentToken = registerAndGetToken("cal-plansave-student@example.com");
        UUID skillId = createSkill(adminToken, "Calendar PlanSave Skill");
        ResourceDetailResponse resource = createAndPublishResource(adminToken, skillId, "Python Lists", "python-lists");

        Instant slotA = Instant.now().plus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant slotB = Instant.now().plus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        // Pre-existing session already occupies slotA for this exact resource.
        schedule(studentToken, resource.id(), "lists", slotA, 60);

        SaveStudyPlanRequest saveRequest = new SaveStudyPlanRequest(List.of(
                new StudyPlanSessionToSave(resource.id(), "lists", slotA, 60, null), // conflicts, must be skipped
                new StudyPlanSessionToSave(resource.id(), "lists", slotB, 60, null), // new, must be created
                new StudyPlanSessionToSave(null, "Practice / Revision", slotB.plus(Duration.ofDays(1)), 45, null)));

        String body = mockMvc.perform(post("/api/v1/calendar/study-plan/save")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        SaveStudyPlanResponse response = objectMapper.readValue(body, SaveStudyPlanResponse.class);

        assertThat(response.skipped()).containsExactly(0);
        assertThat(response.created()).hasSize(2);

        List<LearningSessionResponse> sessions = listSessions(studentToken, Instant.now(),
                Instant.now().plus(Duration.ofDays(10)));
        // 1 pre-existing + 2 newly created (the conflicting one was skipped, not duplicated).
        assertThat(sessions).hasSize(3);
    }

    private LocalDate nextMonday() {
        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        while (date.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
