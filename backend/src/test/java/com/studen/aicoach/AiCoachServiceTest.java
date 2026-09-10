package com.studen.aicoach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.common.exception.RateLimitExceededException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.placement.PlacementModuleItemRepository;
import com.studen.practical.CodingLanguage;
import com.studen.practical.EvaluationType;
import com.studen.practical.PracticalAssessmentDetailResponse;
import com.studen.practical.PracticalAssessmentRequest;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptQuestion;
import com.studen.practical.PracticalAttemptQuestionRepository;
import com.studen.practical.PracticalAttemptResponse;
import com.studen.practical.PracticalCodingLanguageRequest;
import com.studen.practical.PracticalQuestionRequest;
import com.studen.practical.PracticalTestCase;
import com.studen.practical.PracticalTestCaseRepository;
import com.studen.practical.PracticalTestCaseRequest;
import com.studen.practical.PracticalType;
import com.studen.practical.WorkspaceType;
import com.studen.practical.execution.ExecutionJob;
import com.studen.practical.execution.ExecutionJobKind;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.practical.execution.ExecutionJobStatus;
import com.studen.practical.execution.ExecutionTestResult;
import com.studen.practical.execution.ExecutionTestResultRepository;
import com.studen.practical.execution.TestOutcomeStatus;
import com.studen.questionbank.Difficulty;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AiCoachService} exercised end-to-end against real repositories (via {@code @SpringBootTest
 * @Transactional}, same idiom as {@code com.studen.integrity.AssessmentIntegrityControllerTest})
 * with only the outbound OpenAI HTTP call mocked, using {@link OpenAiCoachClient}'s package-private
 * test-seam constructor exactly the way {@code RemoteCodeExecutionServiceTest} mocks the execution
 * server. {@link AiCoachService} itself is hand-constructed per test (not Spring-managed) so each
 * test can swap in its own {@link OpenAiCoachClient}/{@link AiCoachProperties} without a global
 * mocking framework, consistent with this codebase's existing testing conventions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000", "app.execution.enabled=false"})
class AiCoachServiceTest {

    private static final String OPENAI_BASE_URL = "http://openai.test/v1";
    private static final String HIDDEN_SECRET_OUTPUT = "SECRET_HIDDEN_EXPECTED_OUTPUT_MUST_NEVER_LEAK";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticalAttemptQuestionRepository attemptQuestionRepository;

    @Autowired
    private PracticalTestCaseRepository testCaseRepository;

    @Autowired
    private ExecutionJobRepository executionJobRepository;

    @Autowired
    private ExecutionTestResultRepository executionTestResultRepository;

    @Autowired
    private AiCoachInteractionRepository interactionRepository;

    @Autowired
    private PlacementModuleItemRepository moduleItemRepository;

    @Autowired
    private AiCoachPromptBuilder promptBuilder;

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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "AI Coach Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private UUID publishCodingAssessment(String adminToken, UUID skillId, String title) throws Exception {
        PracticalQuestionRequest question = new PracticalQuestionRequest(null, title, null, null,
                "Given two integers, return their sum.", null, null, null, 100, 0,
                List.of(new PracticalCodingLanguageRequest(CodingLanguage.PYTHON, "# write your solution")),
                List.of(new PracticalTestCaseRequest("1 2", "3", false, 0, null),
                        new PracticalTestCaseRequest("hidden-in", HIDDEN_SECRET_OUTPUT, true, 1, null)),
                null);
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.CODING,
                WorkspaceType.CODE_EDITOR, Difficulty.MEDIUM, 30, "Complete this practical assessment.",
                EvaluationType.AUTOMATED, null, List.of(question));
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

    private void createPortfolioWithSkill(String token, UUID skillId) throws Exception {
        String requestJson = "{\"headline\":\"Test Student\",\"skillIds\":[\"" + skillId + "\"]}";
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
    }

    private PracticalAttemptResponse startAttempt(String studentToken, UUID skillId, UUID assessmentId) throws Exception {
        createPortfolioWithSkill(studentToken, skillId);
        String body = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAttemptResponse.class);
    }

    private UUID userIdByEmail(String email) {
        return userRepository.findByEmail(email).map(User::getId).orElseThrow();
    }

    private record MockedClient(OpenAiCoachClient client, MockRestServiceServer server) {
    }

    private MockedClient mockOpenAi(AiCoachProperties properties) {
        properties.setApiKey("test-key");
        properties.setBaseUrl(OPENAI_BASE_URL);
        RestClient.Builder builder = RestClient.builder().baseUrl(OPENAI_BASE_URL)
                .defaultHeader("Authorization", "Bearer test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new MockedClient(new OpenAiCoachClient(builder.build(), properties, true), server);
    }

    private void expectChatCompletion(MockRestServiceServer server, String replyText) {
        String responseJson = """
                {"choices":[{"message":{"role":"assistant","content":%s}}]}
                """.formatted(objectMapper.writeValueAsString(replyText));
        server.expect(requestTo(OPENAI_BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    }

    private AiCoachService buildService(OpenAiCoachClient client, AiCoachProperties properties) {
        return new AiCoachService(attemptQuestionRepository, interactionRepository, executionJobRepository,
                executionTestResultRepository, moduleItemRepository, promptBuilder, client, properties);
    }

    @Test
    void requestInteraction_hint_progressesLevelAcrossSuccessiveCalls() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-hint-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-hint-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Hint Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Hint Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID userId = userIdByEmail("aicoach-hint-student@example.com");
        UUID attemptQuestionId = attempt.questions().get(0).id();

        AiCoachProperties properties = new AiCoachProperties();
        MockedClient mocked = mockOpenAi(properties);
        AiCoachService service = buildService(mocked.client(), properties);

        // MockRestServiceServer requires every expectation registered before the first request is
        // dispatched (SimpleRequestExpectationManager) -- both hints are stubbed up front, then
        // consumed in order by the two sequential service calls below.
        expectChatCompletion(mocked.server(), "Think about what you need to remember while scanning the array.");
        expectChatCompletion(mocked.server(), "Can you use a structure that lets you check if a value was already seen?");

        AiCoachInteractionResponse first = service.requestInteraction(userId, attempt.id(), attemptQuestionId,
                AiCoachActionType.HINT, null);
        assertThat(first.hintLevel()).isEqualTo(1);

        AiCoachInteractionResponse second = service.requestInteraction(userId, attempt.id(), attemptQuestionId,
                AiCoachActionType.HINT, null);
        assertThat(second.hintLevel()).isEqualTo(2);

        List<AiCoachInteractionResponse> history = service.getHistory(userId, attempt.id(), attemptQuestionId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).hintLevel()).isEqualTo(1);
        assertThat(history.get(1).hintLevel()).isEqualTo(2);
    }

    @Test
    void requestInteraction_explainConcept_persistsTopicAndReturnsMessage() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-concept-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-concept-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Concept Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Concept Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID userId = userIdByEmail("aicoach-concept-student@example.com");
        UUID attemptQuestionId = attempt.questions().get(0).id();

        AiCoachProperties properties = new AiCoachProperties();
        MockedClient mocked = mockOpenAi(properties);
        AiCoachService service = buildService(mocked.client(), properties);

        expectChatCompletion(mocked.server(), "A HashMap lets you look up a value in constant time...");
        AiCoachInteractionResponse response = service.requestInteraction(userId, attempt.id(), attemptQuestionId,
                AiCoachActionType.EXPLAIN_CONCEPT, "HashMap");

        assertThat(response.hintLevel()).isNull();
        assertThat(response.message()).contains("HashMap");
        AiCoachInteraction saved = interactionRepository.findAllByPracticalAttemptQuestionIdAndUserIdOrderByCreatedAtAsc(
                attemptQuestionId, userId).get(0);
        assertThat(saved.getRequestTopic()).isEqualTo("HashMap");
    }

    @Test
    void requestInteraction_debugAction_neverSendsHiddenTestContentToOpenAi() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-debug-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-debug-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Debug Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Debug Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID userId = userIdByEmail("aicoach-debug-student@example.com");
        UUID attemptQuestionId = attempt.questions().get(0).id();
        UUID practicalQuestionId = attempt.questions().get(0).practicalQuestionId();

        // Simulate a real prior Run: one visible test failed, one hidden test outcome exists too --
        // the hidden test's own expected/actual content must never reach the OpenAI request body.
        PracticalAttemptQuestion attemptQuestion = attemptQuestionRepository.findById(attemptQuestionId).orElseThrow();
        List<PracticalTestCase> testCases =
                testCaseRepository.findAllByPracticalQuestionIdOrderByDisplayOrderAsc(practicalQuestionId);
        PracticalTestCase visibleCase = testCases.stream().filter(tc -> !tc.isHidden()).findFirst().orElseThrow();
        PracticalTestCase hiddenCase = testCases.stream().filter(PracticalTestCase::isHidden).findFirst().orElseThrow();

        ExecutionJob job = new ExecutionJob(attemptQuestion.getPracticalAttempt(), attemptQuestion,
                ExecutionJobKind.SUBMIT, CodingLanguage.PYTHON, "print(1)");
        job.setStatus(ExecutionJobStatus.COMPLETED);
        job.setTestsPassed(1);
        job.setTestsTotal(2);
        executionJobRepository.save(job);
        executionTestResultRepository.save(new ExecutionTestResult(job, visibleCase, false, false, "2",
                5L, TestOutcomeStatus.WRONG_ANSWER));
        executionTestResultRepository.save(new ExecutionTestResult(job, hiddenCase, true, true,
                HIDDEN_SECRET_OUTPUT, 5L, TestOutcomeStatus.PASSED));

        AiCoachProperties properties = new AiCoachProperties();
        MockedClient mocked = mockOpenAi(properties);
        mocked.server().expect(requestTo(OPENAI_BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).doesNotContain(HIDDEN_SECRET_OUTPUT);
                    assertThat(body).contains("Hidden tests (contents withheld): 1/1");
                })
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"It looks like your output is off by one."}}]}
                        """, MediaType.APPLICATION_JSON));

        AiCoachService service = buildService(mocked.client(), properties);
        AiCoachInteractionResponse response = service.requestInteraction(userId, attempt.id(), attemptQuestionId,
                AiCoachActionType.DEBUG, null);

        assertThat(response.message()).isNotBlank();
        mocked.server().verify();
    }

    @Test
    void requestInteraction_anotherStudentsAttempt_throwsResourceNotFound() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-owner-admin@example.com");
        String ownerToken = registerAndGetToken("aicoach-owner-student@example.com");
        registerAndGetToken("aicoach-intruder-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Ownership Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Ownership Problem");
        PracticalAttemptResponse attempt = startAttempt(ownerToken, skillId, assessmentId);
        UUID intruderId = userIdByEmail("aicoach-intruder-student@example.com");
        UUID attemptQuestionId = attempt.questions().get(0).id();

        AiCoachProperties properties = new AiCoachProperties();
        MockedClient mocked = mockOpenAi(properties);
        AiCoachService service = buildService(mocked.client(), properties);

        assertThatThrownBy(() -> service.requestInteraction(intruderId, attempt.id(), attemptQuestionId,
                AiCoachActionType.HINT, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getHistory(intruderId, attempt.id(), attemptQuestionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requestInteraction_rateLimitExceeded_throwsWithoutCallingOpenAiAgain() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-ratelimit-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-ratelimit-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Rate Limit Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Rate Limit Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID userId = userIdByEmail("aicoach-ratelimit-student@example.com");
        UUID attemptQuestionId = attempt.questions().get(0).id();

        AiCoachProperties properties = new AiCoachProperties();
        properties.setMaxRequestsPerWindow(1);
        MockedClient mocked = mockOpenAi(properties);
        AiCoachService service = buildService(mocked.client(), properties);

        expectChatCompletion(mocked.server(), "Explore two pointers moving from both ends.");
        service.requestInteraction(userId, attempt.id(), attemptQuestionId, AiCoachActionType.EXPLAIN_PATTERN, null);

        // No second stub registered -- if the rate limit didn't trip before the HTTP call, this
        // would fail with "no further requests expected" instead of RateLimitExceededException.
        assertThatThrownBy(() -> service.requestInteraction(userId, attempt.id(), attemptQuestionId,
                AiCoachActionType.EXPLAIN_PATTERN, null)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void requestInteraction_unavailableClient_throwsAiCoachUnavailable() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-unavail-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-unavail-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Unavailable Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Unavailable Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID userId = userIdByEmail("aicoach-unavail-student@example.com");
        UUID attemptQuestionId = attempt.questions().get(0).id();

        AiCoachProperties unconfigured = new AiCoachProperties();
        RestClient.Builder builder = RestClient.builder();
        OpenAiCoachClient unconfiguredClient = new OpenAiCoachClient(builder.build(), unconfigured, false);
        AiCoachService service = buildService(unconfiguredClient, unconfigured);

        assertThatThrownBy(() -> service.requestInteraction(userId, attempt.id(), attemptQuestionId,
                AiCoachActionType.SOLUTION, null)).isInstanceOf(AiCoachUnavailableException.class);
    }
}
