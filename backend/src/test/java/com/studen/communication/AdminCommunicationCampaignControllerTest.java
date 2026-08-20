package com.studen.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.notification.NotificationRepository;
import com.studen.notification.NotificationType;
import com.studen.portfolio.PortfolioRequest;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CommunicationTestSupport.class)
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000",
        "app.communication.async.enabled=false"})
class AdminCommunicationCampaignControllerTest {

    private static final String BASE = "/api/v1/admin/communications/campaigns";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FakeEmailService fakeEmailService;

    // FakeEmailService is a singleton bean shared across every test method in this class (and
    // potentially other test classes reusing the same cached Spring context) — @Transactional
    // rollback only undoes database writes, not in-memory state on a shared bean, so it must be
    // reset explicitly or messages from an earlier test leak into a later one's assertions.
    @BeforeEach
    void resetFakeEmailService() {
        fakeEmailService.reset();
    }

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Communications Test"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private void createPortfolioWithSkill(String token, UUID skillId) throws Exception {
        PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null,
                Set.of(skillId), null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    // Not "{}" — AudienceFilterParser only treats a blank/null string as "match everyone"; a bare
    // {} object has neither operator+children nor field, which is (deliberately) an invalid
    // filter, not a silent match-all — see AudienceFilterParser.parseNode.
    private static final String MATCH_ALL_FILTER = "{\"operator\":\"AND\",\"children\":[]}";

    private CampaignRequest draftRequest(String name, String filterJson, boolean marketing) {
        return new CampaignRequest(name, CommunicationCategory.CUSTOM, marketing, filterJson, null, null, true, false,
                false, "Subject line", "<p>Hello {{firstName}}</p>", null, null, null, null, "View", "https://studen.app");
    }

    private UUID createDraft(String adminToken, CampaignRequest request) throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CampaignDetailResponse.class).id();
    }

    // --- Authorization -----------------------------------------------------------------------

    @Test
    void list_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void create_asStudent_returns403() throws Exception {
        String studentToken = registerAndGetToken("cc-student@example.com");
        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftRequest("X", MATCH_ALL_FILTER, false))))
                .andExpect(status().isForbidden());
    }

    // --- Validation ----------------------------------------------------------------------------

    @Test
    void create_withNoChannelSelected_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-nochannel-admin@example.com");
        CampaignRequest request = new CampaignRequest("No channel", CommunicationCategory.CUSTOM, false, "{}", null,
                null, false, false, false, null, null, null, null, null, null, null, null);
        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- Draft lifecycle -------------------------------------------------------------------------

    @Test
    void updateCampaign_whileDraft_succeeds_butAfterSendReturns409() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-lifecycle-admin@example.com");
        UUID campaignId = createDraft(adminToken, draftRequest("Lifecycle", MATCH_ALL_FILTER, false));

        mockMvc.perform(patch(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftRequest("Lifecycle Renamed", MATCH_ALL_FILTER, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lifecycle Renamed"));

        mockMvc.perform(post(BASE + "/" + campaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftRequest("Should Fail", MATCH_ALL_FILTER, false))))
                .andExpect(status().isConflict());
    }

    // --- Send now: full delivery + per-channel tracking -------------------------------------------

    @Test
    void sendNow_deliversEmailPushAndInApp_andRecordsPerRecipientStatus() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-send-admin@example.com");
        UUID skillId = createSkill(adminToken, "CC Send Skill");
        String studentEmail = "cc-send-student@example.com";
        createPortfolioWithSkill(registerAndGetToken(studentEmail), skillId);

        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillId.toString())));
        CampaignRequest request = new CampaignRequest("All-channel send", CommunicationCategory.CUSTOM, false, filter,
                null, null, true, true, true, "Hi {{firstName}}", "<p>Body</p>", "Push title", "Push body",
                "In-app title", "In-app body", "View", "https://studen.app");
        UUID campaignId = createDraft(adminToken, request);

        mockMvc.perform(post(BASE + "/" + campaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.resolvedRecipientCount").value(1));

        assertThat(fakeEmailService.sent()).hasSize(1);
        assertThat(fakeEmailService.sent().get(0).to()).isEqualTo(studentEmail);
        assertThat(fakeEmailService.sent().get(0).subject()).isEqualTo("Hi Test");

        User student = userRepository.findByEmail(studentEmail).orElseThrow();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(student.getId(),
                org.springframework.data.domain.PageRequest.of(0, 5)))
                .anyMatch(n -> n.getType() == NotificationType.ADMIN_MESSAGE);

        mockMvc.perform(get(BASE + "/" + campaignId + "/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email.SENT").value(1))
                .andExpect(jsonPath("$.push.SENT").value(1))
                .andExpect(jsonPath("$.inapp.SENT").value(1));
    }

    // --- Partial failure never fails the whole campaign --------------------------------------------

    @Test
    void sendNow_onePartnerFails_campaignEndsUpPartiallySent_andRetryOnlyResendsTheFailedOne() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-partial-admin@example.com");
        UUID skillId = createSkill(adminToken, "CC Partial Skill");
        String goodEmail = "cc-partial-good@example.com";
        String badEmail = "cc-partial-bad@example.com";
        createPortfolioWithSkill(registerAndGetToken(goodEmail), skillId);
        createPortfolioWithSkill(registerAndGetToken(badEmail), skillId);

        fakeEmailService.failFor(badEmail);

        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillId.toString())));
        UUID campaignId = createDraft(adminToken, draftRequest("Partial", filter, false));

        mockMvc.perform(post(BASE + "/" + campaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("PARTIALLY_SENT"));

        // Retry: only the failed recipient gets re-attempted, never the already-successful one.
        fakeEmailService.reset();
        mockMvc.perform(post(BASE + "/" + campaignId + "/retry-failed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(fakeEmailService.sent()).hasSize(1);
        assertThat(fakeEmailService.sent().get(0).to()).isEqualTo(badEmail);

        mockMvc.perform(get(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void failedRecipients_returnsTheRealProviderErrorForTheFailedRecipientOnly() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-failure-detail-admin@example.com");
        UUID skillId = createSkill(adminToken, "CC Failure Detail Skill");
        String goodEmail = "cc-failure-detail-good@example.com";
        String badEmail = "cc-failure-detail-bad@example.com";
        createPortfolioWithSkill(registerAndGetToken(goodEmail), skillId);
        createPortfolioWithSkill(registerAndGetToken(badEmail), skillId);

        fakeEmailService.failFor(badEmail);

        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillId.toString())));
        UUID campaignId = createDraft(adminToken, draftRequest("Failure detail", filter, false));

        mockMvc.perform(post(BASE + "/" + campaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + campaignId + "/recipients/failed?channel=EMAIL")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientEmail").value(badEmail))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].errorMessage").value("Simulated provider failure for test"));

        // A channel with zero FAILED rows (push/in-app both succeeded) returns an empty list, not
        // an error — proves this only ever surfaces real recorded failures, never fabricates one.
        mockMvc.perform(get(BASE + "/" + campaignId + "/recipients/failed?channel=PUSH")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void retryFailed_withNothingToRetry_returns409() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-noretry-admin@example.com");
        UUID campaignId = createDraft(adminToken, draftRequest("Empty audience", "{\"field\":\"USER_SPECIFIC_IDS\",\"params\":{\"userIds\":\""
                + UUID.randomUUID() + "\"}}", false));

        mockMvc.perform(post(BASE + "/" + campaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(BASE + "/" + campaignId + "/retry-failed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // --- Marketing opt-out --------------------------------------------------------------------------

    @Test
    void marketingCampaign_skipsOptedOutUsers_transactionalCampaignDoesNot() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-optout-admin@example.com");
        UUID skillId = createSkill(adminToken, "CC OptOut Skill");
        String optedOutEmail = "cc-optout-user@example.com";
        createPortfolioWithSkill(registerAndGetToken(optedOutEmail), skillId);
        User optedOut = userRepository.findByEmail(optedOutEmail).orElseThrow();
        optedOut.setMarketingOptOut(true);
        userRepository.save(optedOut);

        String filter = objectMapper.writeValueAsString(
                java.util.Map.of("field", "SKILL_HAS", "params", java.util.Map.of("skillId", skillId.toString())));

        UUID marketingCampaignId = createDraft(adminToken, draftRequest("Marketing blast", filter, true));
        mockMvc.perform(post(BASE + "/" + marketingCampaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + marketingCampaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.resolvedRecipientCount").value(0));

        UUID transactionalCampaignId = createDraft(adminToken, draftRequest("Transactional update", filter, false));
        mockMvc.perform(post(BASE + "/" + transactionalCampaignId + "/send-now")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + transactionalCampaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.resolvedRecipientCount").value(1));
    }

    // --- Scheduling ------------------------------------------------------------------------------

    @Test
    void schedule_requiresFutureTime_andLocksCampaignFromEditingOnceScheduled() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-schedule-admin@example.com");
        UUID campaignId = createDraft(adminToken, draftRequest("Scheduled", MATCH_ALL_FILTER, false));

        mockMvc.perform(post(BASE + "/" + campaignId + "/schedule")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScheduleCampaignRequest(java.time.Instant.now().minusSeconds(60)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE + "/" + campaignId + "/schedule")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleCampaignRequest(java.time.Instant.now().plusSeconds(3600)))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(patch(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftRequest("Edit attempt", MATCH_ALL_FILTER, false))))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_scheduledCampaign_succeeds() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-cancel-admin@example.com");
        UUID campaignId = createDraft(adminToken, draftRequest("Cancel me", MATCH_ALL_FILTER, false));
        mockMvc.perform(post(BASE + "/" + campaignId + "/schedule")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ScheduleCampaignRequest(java.time.Instant.now().plusSeconds(3600)))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(BASE + "/" + campaignId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void list_returnsPagedCampaigns() throws Exception {
        String adminToken = registerAdminAndGetToken("cc-list-admin@example.com");
        createDraft(adminToken, draftRequest("List Campaign 1", MATCH_ALL_FILTER, false));
        createDraft(adminToken, draftRequest("List Campaign 2", MATCH_ALL_FILTER, false));

        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
