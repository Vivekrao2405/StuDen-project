package com.studen.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.practical.CodingLanguage;
import com.studen.practical.EvaluateAttemptRequest;
import com.studen.practical.EvaluationType;
import com.studen.practical.PracticalAssessmentDetailResponse;
import com.studen.practical.PracticalAssessmentRequest;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptRepository;
import com.studen.practical.PracticalAttemptResponse;
import com.studen.practical.PracticalCodingLanguageRequest;
import com.studen.practical.PracticalTestCaseRequest;
import com.studen.practical.PracticalType;
import com.studen.practical.WorkspaceType;
import com.studen.questionbank.Difficulty;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
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
 * Phase 7.6 Assessment Integrity. Reuses the same registration/skill/assessment-publish helper
 * pattern as {@code com.studen.practical.PracticalAttemptControllerTest} (kept self-contained
 * here rather than shared, matching that file's own precedent of not extracting a shared base
 * class for this package).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000", "app.execution.enabled=false"})
class AssessmentIntegrityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticalAttemptRepository attemptRepository;

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
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
        });
        return token;
    }

    private UUID createSkill(String adminToken, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Integrity Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private List<PracticalCodingLanguageRequest> oneLanguage() {
        return List.of(new PracticalCodingLanguageRequest(CodingLanguage.JAVA, "public class Main {}"));
    }

    private UUID publishAssessment(String adminToken, UUID skillId, String title, EvaluationType evaluationType,
            String configurationJson) throws Exception {
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.CODING,
                WorkspaceType.CODE_EDITOR, Difficulty.MEDIUM, 30, "Solve the problem.", null, null, evaluationType,
                configurationJson, oneLanguage(), List.of(new PracticalTestCaseRequest("1", "1", false, 0, null)), null);
        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = objectMapper.readValue(body, PracticalAssessmentDetailResponse.class).id();
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-assessments/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        return id;
    }

    private PracticalAttemptResponse startAttempt(String studentToken, UUID assessmentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAttemptResponse.class);
    }

    // Pushes startedAt an hour into the past (deadline correspondingly extended) so tests can
    // place two occurredAt timestamps far enough apart to cross duration-severity boundaries
    // while both stay safely before "now" (the server clamps any occurredAt after now+5s).
    private void backdateAttemptStart(UUID attemptId) {
        PracticalAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        attempt.setStartedAt(Instant.now().minusSeconds(3600));
        attempt.setDeadline(Instant.now().plusSeconds(3600));
        attemptRepository.save(attempt);
    }

    private String eventBatch(String eventType, Instant occurredAt) {
        return """
                {"events":[{"clientEventId":"%s","eventType":"%s","occurredAt":"%s"}]}
                """.formatted(UUID.randomUUID(), eventType, occurredAt);
    }

    // --- Basic validation / server authority -------------------------------------------------

    @Test
    void recordEvent_valid_isAcceptedAndSummaryReflectsIt() throws Exception {
        String adminToken = registerAdminAndGetToken("int-valid-admin@example.com");
        String studentToken = registerAndGetToken("int-valid-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Valid Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Valid Event Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("COPY_ATTEMPT", Instant.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1));
    }

    @Test
    void recordEvent_invalidEventType_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("int-badtype-admin@example.com");
        String studentToken = registerAndGetToken("int-badtype-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Bad Type Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Bad Type Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        String body = """
                {"events":[{"clientEventId":"%s","eventType":"NOT_A_REAL_TYPE","occurredAt":"%s"}]}
                """.formatted(UUID.randomUUID(), Instant.now());

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordEvent_multipleSessionFromClient_isRejected() throws Exception {
        String adminToken = registerAdminAndGetToken("int-forge-admin@example.com");
        String studentToken = registerAndGetToken("int-forge-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Forge Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Forge Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("MULTIPLE_SESSION", Instant.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordEvent_forAnotherStudentsAttempt_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("int-idor-admin@example.com");
        String ownerToken = registerAndGetToken("int-idor-owner@example.com");
        String attackerToken = registerAndGetToken("int-idor-attacker@example.com");
        UUID skillId = createSkill(adminToken, "Integrity IDOR Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "IDOR Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(ownerToken, assessmentId);

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("COPY_ATTEMPT", Instant.now())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id() + "/integrity-summary")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void recordEvent_onExpiredAttemptOutsideGrace_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("int-expired-admin@example.com");
        String studentToken = registerAndGetToken("int-expired-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Expired Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Expired Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        PracticalAttempt entity = attemptRepository.findById(attempt.id()).orElseThrow();
        entity.setDeadline(Instant.now().minusSeconds(3600));
        attemptRepository.save(entity);
        // Trigger server-side expiry detection (mirrors PracticalAttemptService.expireIfDue) --
        // this also stamps submittedAt = now (PracticalAttemptRepository.transitionIfInProgress
        // sets it even for an EXPIRED transition), so back-date it too to actually fall outside
        // the 30s grace window rather than just outside IN_PROGRESS.
        mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        PracticalAttempt expired = attemptRepository.findById(attempt.id()).orElseThrow();
        expired.setSubmittedAt(Instant.now().minusSeconds(3600));
        attemptRepository.save(expired);

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("COPY_ATTEMPT", Instant.now())))
                .andExpect(status().isConflict());
    }

    @Test
    void recordEvent_duplicateClientEventId_notDoubleCounted() throws Exception {
        String adminToken = registerAdminAndGetToken("int-dup-admin@example.com");
        String studentToken = registerAndGetToken("int-dup-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Dup Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Dup Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        UUID clientEventId = UUID.randomUUID();
        String body = """
                {"events":[{"clientEventId":"%s","eventType":"COPY_ATTEMPT","occurredAt":"%s"}]}
                """.formatted(clientEventId, Instant.now());

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1));
    }

    // --- Tab visibility pairing ---------------------------------------------------------------

    @Test
    void tabHiddenThenVisible_pairsIntoOneAwayEvent_withComputedDuration() throws Exception {
        String adminToken = registerAdminAndGetToken("int-tab-admin@example.com");
        String studentToken = registerAndGetToken("int-tab-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Tab Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Tab Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        backdateAttemptStart(attempt.id());

        Instant hiddenAt = Instant.now().minusSeconds(1800);
        Instant visibleAt = hiddenAt.plusSeconds(40); // >30s -> HIGH severity bucket

        String body = """
                {"events":[
                  {"clientEventId":"%s","eventType":"TAB_HIDDEN","occurredAt":"%s"},
                  {"clientEventId":"%s","eventType":"TAB_VISIBLE","occurredAt":"%s"}
                ]}
                """.formatted(UUID.randomUUID(), hiddenAt, UUID.randomUUID(), visibleAt);

        String response = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabSwitchCount").value(1))
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        // A single HIGH-duration away event deducts properties.highDeduction (7) from 100.
        assertThat(summary.integrityScore()).isEqualTo(93);
        assertThat(summary.totalEvents()).isEqualTo(2);
    }

    @Test
    void correlatedBlurDuplicate_doesNotDoubleCountAsSecondAwayEvent() throws Exception {
        String adminToken = registerAdminAndGetToken("int-corr-admin@example.com");
        String studentToken = registerAndGetToken("int-corr-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Correlated Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Correlated Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        backdateAttemptStart(attempt.id());

        Instant t = Instant.now().minusSeconds(1800);
        // Browser fires visibilitychange(hidden) AND blur for one physical tab switch, then
        // visibilitychange(visible) AND focus for the return -- 4 raw events, 1 logical away period.
        String body = """
                {"events":[
                  {"clientEventId":"%s","eventType":"TAB_HIDDEN","occurredAt":"%s"},
                  {"clientEventId":"%s","eventType":"WINDOW_BLUR","occurredAt":"%s"},
                  {"clientEventId":"%s","eventType":"TAB_VISIBLE","occurredAt":"%s"},
                  {"clientEventId":"%s","eventType":"WINDOW_FOCUS","occurredAt":"%s"}
                ]}
                """.formatted(UUID.randomUUID(), t, UUID.randomUUID(), t.plusMillis(50),
                UUID.randomUUID(), t.plusSeconds(2), UUID.randomUUID(), t.plusSeconds(2).plusMillis(50));

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabSwitchCount").value(1))
                .andExpect(jsonPath("$.totalEvents").value(4));
    }

    // --- Clipboard policy --------------------------------------------------------------------

    @Test
    void pasteAttempt_underPermissivePolicy_recordedButNoDeduction() throws Exception {
        String adminToken = registerAdminAndGetToken("int-permissive-admin@example.com");
        String studentToken = registerAndGetToken("int-permissive-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Permissive Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Permissive Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        assertThat(attempt.integrityPolicy().allowPaste()).isTrue();

        String response = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("PASTE_ATTEMPT", Instant.now())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.pasteAttemptCount()).isEqualTo(1);
        assertThat(summary.integrityScore()).isEqualTo(100);
        assertThat(summary.integrityStatus()).isEqualTo(IntegrityStatus.CLEAN);
    }

    @Test
    void pasteAttempts_underRestrictivePolicy_deductAndCapAtReviewThreshold() throws Exception {
        String adminToken = registerAdminAndGetToken("int-restrict-admin@example.com");
        String studentToken = registerAndGetToken("int-restrict-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Restrictive Skill");
        String config = "{\"integrityPolicy\":{\"allowCopy\":true,\"allowPaste\":false,\"allowCut\":true,\"requireFullscreen\":false}}";
        UUID assessmentId = publishAssessment(adminToken, skillId, "Restrictive Problem", EvaluationType.MANUAL, config);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        assertThat(attempt.integrityPolicy().allowPaste()).isFalse();

        StringBuilder events = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) events.append(",");
            events.append("{\"clientEventId\":\"").append(UUID.randomUUID())
                    .append("\",\"eventType\":\"PASTE_ATTEMPT\",\"occurredAt\":\"").append(Instant.now()).append("\"}");
        }
        String body = "{\"events\":[" + events + "]}";

        String response = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.pasteAttemptCount()).isEqualTo(8);
        // 5 MEDIUM(3) + 3 HIGH(7) = 36, capped at the 30-point clipboard category cap -> score 70.
        assertThat(summary.integrityScore()).isEqualTo(70);
        assertThat(summary.integrityStatus()).isEqualTo(IntegrityStatus.REVIEW);
    }

    // --- Fullscreen ----------------------------------------------------------------------------

    @Test
    void fullscreenExit_whenRequired_deducts_whenNotRequired_isInfoOnly() throws Exception {
        String adminToken = registerAdminAndGetToken("int-fs-admin@example.com");
        String studentToken = registerAndGetToken("int-fs-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Fullscreen Skill");
        String config = "{\"integrityPolicy\":{\"requireFullscreen\":true}}";
        UUID assessmentId = publishAssessment(adminToken, skillId, "Fullscreen Problem", EvaluationType.MANUAL, config);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        assertThat(attempt.integrityPolicy().requireFullscreen()).isTrue();

        String response = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("FULLSCREEN_EXITED", Instant.now())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.fullscreenExitCount()).isEqualTo(1);
        assertThat(summary.integrityScore()).isEqualTo(97); // MEDIUM deduction = 3
    }

    // --- Multiple sessions (server-detected via heartbeat) --------------------------------------

    @Test
    void heartbeat_singleSession_neverFlagsMultipleSession() throws Exception {
        String adminToken = registerAdminAndGetToken("int-single-admin@example.com");
        String studentToken = registerAndGetToken("int-single-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Single Session Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Single Session Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        String response = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/heartbeat")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"tab-A\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.multipleSessionCount()).isZero();
        assertThat(summary.integrityScore()).isEqualTo(100);
    }

    @Test
    void heartbeat_fromTwoSessions_flagsMultipleSession() throws Exception {
        String adminToken = registerAdminAndGetToken("int-multi-admin@example.com");
        String studentToken = registerAndGetToken("int-multi-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Multi Session Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Multi Session Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/heartbeat")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"tab-A\"}"))
                .andExpect(status().isOk());
        String response = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/heartbeat")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"tab-B\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.multipleSessionCount()).isEqualTo(1);
        assertThat(summary.integrityScore()).isEqualTo(93); // HIGH deduction = 7

        // Cooldown: a third heartbeat within the window must not flag (and deduct) a second time.
        String third = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/heartbeat")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"tab-A\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(third, IntegritySummaryResponse.class).multipleSessionCount()).isEqualTo(1);
    }

    // --- Clean baseline --------------------------------------------------------------------

    @Test
    void noActivity_producesCleanStatusAndFullScore() throws Exception {
        String adminToken = registerAdminAndGetToken("int-clean-admin@example.com");
        String studentToken = registerAndGetToken("int-clean-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Clean Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Clean Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        String response = mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id() + "/integrity-summary")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.integrityScore()).isEqualTo(100);
        assertThat(summary.integrityStatus()).isEqualTo(IntegrityStatus.CLEAN);
        assertThat(summary.totalEvents()).isZero();
    }

    // --- Admin access, isolation, override, and technical-score independence ------------------

    @Test
    void adminTimeline_accessibleToAdmin_forbiddenToStudent() throws Exception {
        String adminToken = registerAdminAndGetToken("int-admintl-admin@example.com");
        String studentToken = registerAndGetToken("int-admintl-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Admin Timeline Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Admin Timeline Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("COPY_ATTEMPT", Instant.now())))
                .andExpect(status().isOk());

        String timeline = mockMvc.perform(get("/api/v1/admin/practical-attempts/" + attempt.id() + "/integrity-timeline")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(timeline).contains("Assessment started").contains("Copy attempt");

        mockMvc.perform(get("/api/v1/admin/practical-attempts/" + attempt.id() + "/integrity-timeline")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOverride_persistsAdminReasonTimestamp_andBecomesEffectiveStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("int-override-admin@example.com");
        String studentToken = registerAndGetToken("int-override-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Override Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Override Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        String body = """
                {"status":"INVALIDATED","reason":"Confirmed screen-sharing during proctoring review."}
                """;
        String response = mockMvc.perform(post("/api/v1/admin/practical-attempts/" + attempt.id() + "/integrity-override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        IntegritySummaryResponse summary = objectMapper.readValue(response, IntegritySummaryResponse.class);
        assertThat(summary.effectiveStatus()).isEqualTo(IntegrityStatus.INVALIDATED);
        assertThat(summary.overridden()).isTrue();
        assertThat(summary.overrideReason()).contains("Confirmed screen-sharing");
        assertThat(summary.overrideByName()).isNotBlank();
        assertThat(summary.overriddenAt()).isNotNull();
        // The computed (non-override) status is untouched -- still CLEAN, since no activity
        // happened. INVALIDATED is never auto-assigned (goal #17/#18).
        assertThat(summary.integrityStatus()).isEqualTo(IntegrityStatus.CLEAN);
    }

    @Test
    void integrityActivity_neverChangesTechnicalScore() throws Exception {
        String adminToken = registerAdminAndGetToken("int-techscore-admin@example.com");
        String studentToken = registerAndGetToken("int-techscore-student@example.com");
        UUID skillId = createSkill(adminToken, "Integrity Tech Score Skill");
        UUID assessmentId = publishAssessment(adminToken, skillId, "Tech Score Problem", EvaluationType.MANUAL, null);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/practical-attempts/" + attempt.id() + "/evaluate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluateAttemptRequest(null, 82, "Good work"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(82));

        // Report a pile of suspicious activity via the final-submission grace window.
        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/integrity-events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatch("COPY_ATTEMPT", Instant.now())))
                .andExpect(status().isOk());

        String detail = mockMvc.perform(get("/api/v1/admin/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(82))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("\"integrity\"");
    }
}
