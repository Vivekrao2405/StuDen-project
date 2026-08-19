package com.studen.practical.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
import com.studen.practical.AdminTestRunRequest;
import com.studen.practical.AdminTestRunResponse;
import com.studen.practical.CodingLanguage;
import com.studen.practical.EvaluationType;
import com.studen.practical.PracticalAssessmentDetailResponse;
import com.studen.practical.PracticalAssessmentRepository;
import com.studen.practical.PracticalAssessmentRequest;
import com.studen.practical.PracticalAttemptResponse;
import com.studen.practical.PracticalAttemptResultResponse;
import com.studen.practical.PracticalCodingLanguageRequest;
import com.studen.practical.PracticalTestCaseRequest;
import com.studen.practical.PracticalType;
import com.studen.practical.RunResultResponse;
import com.studen.practical.SaveAttemptRequest;
import com.studen.practical.WorkspaceType;
import com.studen.questionbank.Difficulty;
import com.studen.skill.CreateSkillRequest;
import com.studen.skill.SkillResponse;
import com.studen.user.UserRepository;
import com.studen.user.UserRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Real sandboxed execution against a real Docker Engine + the {@code studen-code-runner} image
 * (build it first: {@code docker build -t studen-code-runner:latest backend/docker/runner}).
 * Skipped entirely — not failed — on any machine without Docker reachable, so the rest of the
 * suite (e.g. {@code PracticalAttemptControllerTest}) never depends on it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
@EnabledIfDockerAvailable
class ExecutionSandboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticalAssessmentRepository practicalAssessmentRepository;

    private String registerAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Test User", email, "SecurePassword123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    private String registerAdminAndGetToken(String email) throws Exception {
        String token = registerAndGetToken(email);
        // Explicit save (not just dirty-checking) -- the concurrency test below runs this outside
        // any Spring-managed transaction (NOT_SUPPORTED), where a mutated-then-detached entity
        // never gets flushed on its own.
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
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, SkillResponse.class).id();
    }

    private UUID publishAssessment(String adminToken, PracticalAssessmentRequest request) throws Exception {
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

    private UUID publishSumProblem(String adminToken, UUID skillId, String title, EvaluationType evaluationType) throws Exception {
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.CODING,
                WorkspaceType.CODE_EDITOR, Difficulty.EASY, 30, "Read two integers, print their sum.", null, null,
                evaluationType, null,
                List.of(new PracticalCodingLanguageRequest(CodingLanguage.JAVA, ""),
                        new PracticalCodingLanguageRequest(CodingLanguage.PYTHON, ""),
                        new PracticalCodingLanguageRequest(CodingLanguage.C, ""),
                        new PracticalCodingLanguageRequest(CodingLanguage.CPP, "")),
                List.of(new PracticalTestCaseRequest("3 4", "7", false, 0, null),
                        new PracticalTestCaseRequest("10 20", "30", true, 1, null)),
                null);
        return publishAssessment(adminToken, request);
    }

    private UUID publishSqlProblem(String adminToken, UUID skillId, String title) throws Exception {
        String seed = "CREATE TABLE students (id INT, name TEXT, score INT); "
                + "INSERT INTO students VALUES (1,'A',90),(2,'B',80),(3,'C',70);";
        PracticalAssessmentRequest request = new PracticalAssessmentRequest(title, skillId, PracticalType.SQL,
                WorkspaceType.SQL_EDITOR, Difficulty.EASY, 30, "Select all student names with score > 75.", null, null,
                EvaluationType.AUTOMATED, "{\"schemaDescription\":\"students(id,name,score)\"}", null,
                List.of(new PracticalTestCaseRequest(seed, "SELECT name FROM students WHERE score > 75", false, 0, null)),
                null);
        return publishAssessment(adminToken, request);
    }

    // Eligibility (see com.studen.portfolio.PortfolioSkillProfileService) requires the assessment's
    // skill to be on the student's portfolio before a *new* attempt can be started. Idempotent.
    private void ensurePortfolioSkill(String token, UUID skillId) throws Exception {
        int status = mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        if (status == 404) {
            PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null, Set.of(skillId), null);
            mockMvc.perform(post("/api/v1/portfolio")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    private PracticalAttemptResponse startAttempt(String studentToken, UUID assessmentId) throws Exception {
        UUID skillId = practicalAssessmentRepository.findById(assessmentId).orElseThrow(AssertionError::new).getSkill().getId();
        ensurePortfolioSkill(studentToken, skillId);
        String body = mockMvc.perform(post("/api/v1/practical-assessments/" + assessmentId + "/attempts")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PracticalAttemptResponse.class);
    }

    private void saveSubmission(String studentToken, UUID attemptId, String content, CodingLanguage language) throws Exception {
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attemptId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest(content, language, null))))
                .andExpect(status().isOk());
    }

    private RunResultResponse run(String studentToken, UUID attemptId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/practical-attempts/" + attemptId + "/run")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, RunResultResponse.class);
    }

    // --- Correct solutions, all four languages -----------------------------------------------

    @Test
    void java_correctSolution_allPublicTestsPass() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-java-ok-admin@example.com");
        String studentToken = registerAndGetToken("sbx-java-ok-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Java Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String solution = "import java.util.Scanner;\n"
                + "public class Main { public static void main(String[] a) { Scanner sc = new Scanner(System.in); "
                + "int x = sc.nextInt(); int y = sc.nextInt(); System.out.println(x + y); } }";
        saveSubmission(studentToken, attempt.id(), solution, CodingLanguage.JAVA);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(1);
        assertThat(result.testsTotal()).isEqualTo(1);
        assertThat(result.publicTestResults().get(0).passed()).isTrue();
    }

    @Test
    void python_correctSolution_allPublicTestsPass() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-py-ok-admin@example.com");
        String studentToken = registerAndGetToken("sbx-py-ok-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Python Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Python Sum", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        saveSubmission(studentToken, attempt.id(), "a, b = map(int, input().split())\nprint(a + b)\n", CodingLanguage.PYTHON);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(1);
    }

    @Test
    void c_correctSolution_allPublicTestsPass() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-c-ok-admin@example.com");
        String studentToken = registerAndGetToken("sbx-c-ok-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox C Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "C Sum", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String solution = "#include <stdio.h>\nint main() { int a, b; scanf(\"%d %d\", &a, &b); printf(\"%d\\n\", a + b); return 0; }";
        saveSubmission(studentToken, attempt.id(), solution, CodingLanguage.C);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(1);
    }

    @Test
    void cpp_correctSolution_allPublicTestsPass() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-cpp-ok-admin@example.com");
        String studentToken = registerAndGetToken("sbx-cpp-ok-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox CPP Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "CPP Sum", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String solution = "#include <iostream>\nusing namespace std;\nint main() { int a, b; cin >> a >> b; cout << a + b << endl; return 0; }";
        saveSubmission(studentToken, attempt.id(), solution, CodingLanguage.CPP);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(1);
    }

    // --- Wrong answer / errors ------------------------------------------------------------------

    @Test
    void java_wrongAnswer_reportedAsFailedTest_notAnError() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-java-wa-admin@example.com");
        String studentToken = registerAndGetToken("sbx-java-wa-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Java WA Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum WA", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String wrong = "import java.util.Scanner;\n"
                + "public class Main { public static void main(String[] a) { Scanner sc = new Scanner(System.in); "
                + "int x = sc.nextInt(); int y = sc.nextInt(); System.out.println(x - y); } }";
        saveSubmission(studentToken, attempt.id(), wrong, CodingLanguage.JAVA);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(0);
        assertThat(result.publicTestResults().get(0).status().name()).isEqualTo("WRONG_ANSWER");
    }

    @Test
    void java_compilationError_returnsSanitizedCompilerMessage_neverHostPaths() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-java-ce-admin@example.com");
        String studentToken = registerAndGetToken("sbx-java-ce-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Java CE Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum CE", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        saveSubmission(studentToken, attempt.id(), "public class Main { public static void main(String[] a) { int x = }",
                CodingLanguage.JAVA);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPILATION_ERROR");
        assertThat(result.compileError()).isNotBlank();
        assertThat(result.compileError()).doesNotContain("C:\\").doesNotContain("/home/").doesNotContain("AppData");
        assertThat(result.testsPassed()).isNull();
    }

    @Test
    void java_runtimeError_classifiedCorrectly() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-java-re-admin@example.com");
        String studentToken = registerAndGetToken("sbx-java-re-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Java RE Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum RE", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        saveSubmission(studentToken, attempt.id(),
                "public class Main { public static void main(String[] a) { int x = 1 / 0; System.out.println(x); } }",
                CodingLanguage.JAVA);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.publicTestResults().get(0).status().name()).isEqualTo("RUNTIME_ERROR");
    }

    @Test
    void python_infiniteLoop_timesOut_ratherThanHangingTheRequest() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-py-timeout-admin@example.com");
        String studentToken = registerAndGetToken("sbx-py-timeout-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Python Timeout Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Python Sum Timeout", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        saveSubmission(studentToken, attempt.id(), "while True:\n    pass\n", CodingLanguage.PYTHON);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.publicTestResults().get(0).status().name()).isEqualTo("TIMEOUT");
    }

    // --- Malicious submissions must be safely contained, not crash the app ----------------------

    @Test
    void python_memoryBomb_containedAsMemoryLimit_orTimeout_neverCrashesTheApp() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-py-mem-admin@example.com");
        String studentToken = registerAndGetToken("sbx-py-mem-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Python Memory Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Python Sum Memory", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        saveSubmission(studentToken, attempt.id(), "x = [0] * (10**9)\nx = [0] * (10**9)\nprint(len(x))\n", CodingLanguage.PYTHON);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        // Contained one way or another -- never a bare crash/500, and never falsely reported PASSED.
        assertThat(result.publicTestResults().get(0).status().name()).isIn("MEMORY_LIMIT", "TIMEOUT", "RUNTIME_ERROR");
    }

    @Test
    void python_networkAttempt_isBlocked_networkNoneEnforced() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-py-net-admin@example.com");
        String studentToken = registerAndGetToken("sbx-py-net-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Python Network Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Python Sum Network", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String probe = "import socket\n"
                + "input()\n"
                + "try:\n"
                + "    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)\n"
                + "    s.settimeout(2)\n"
                + "    s.connect(('8.8.8.8', 53))\n"
                + "    print('NETWORK_REACHED')\n"
                + "except Exception:\n"
                + "    print('NETWORK_BLOCKED')\n";
        saveSubmission(studentToken, attempt.id(), probe, CodingLanguage.PYTHON);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        String actual = result.publicTestResults().get(0).actualOutput();
        assertThat(actual).contains("NETWORK_BLOCKED");
    }

    @Test
    void python_filesystemEscapeAttempt_isBlocked_readOnlyRootfsEnforced() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-py-fs-admin@example.com");
        String studentToken = registerAndGetToken("sbx-py-fs-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Python Filesystem Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Python Sum Filesystem", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String probe = "input()\n"
                + "try:\n"
                + "    open('/etc/studen-escape-test', 'w').write('x')\n"
                + "    print('WRITE_SUCCEEDED')\n"
                + "except Exception:\n"
                + "    print('WRITE_BLOCKED')\n";
        saveSubmission(studentToken, attempt.id(), probe, CodingLanguage.PYTHON);

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.publicTestResults().get(0).actualOutput()).contains("WRITE_BLOCKED");
    }

    @Test
    void python_processSpawnAttempt_boundedByPidsLimit_neverHangsTheRequest() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-py-fork-admin@example.com");
        String studentToken = registerAndGetToken("sbx-py-fork-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Python Fork Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Python Sum Fork", EvaluationType.MANUAL);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String bomb = "import os\n"
                + "input()\n"
                + "try:\n"
                + "    while True:\n"
                + "        os.fork()\n"
                + "except Exception:\n"
                + "    print('FORK_BLOCKED')\n";
        saveSubmission(studentToken, attempt.id(), bomb, CodingLanguage.PYTHON);

        // The request itself must complete within the configured timeout regardless of outcome --
        // proves pids-limit/timeout containment rather than the app hanging or crashing.
        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.publicTestResults().get(0).status()).isNotNull();
    }

    // --- Automated submit path ------------------------------------------------------------------

    @Test
    void submit_automatedCoding_autoScoresAndTransitionsToSubmitted() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-submit-admin@example.com");
        String studentToken = registerAndGetToken("sbx-submit-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Submit Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum Submit", EvaluationType.AUTOMATED);
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        String solution = "import java.util.Scanner;\n"
                + "public class Main { public static void main(String[] a) { Scanner sc = new Scanner(System.in); "
                + "int x = sc.nextInt(); int y = sc.nextInt(); System.out.println(x + y); } }";
        saveSubmission(studentToken, attempt.id(), solution, CodingLanguage.JAVA);

        String body = mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        PracticalAttemptResultResponse result = objectMapper.readValue(body, PracticalAttemptResultResponse.class);

        assertThat(result.status().name()).isEqualTo("SUBMITTED");
        assertThat(result.score()).isEqualTo(100); // both test cases (1 public + 1 hidden) pass
    }

    // --- Admin Test Question tool -----------------------------------------------------------------

    @Test
    void adminTestRun_seesHiddenTestResults_studentEndpointNeverDoes() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-admintest-admin@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Admin Test Skill");
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum AdminTest", EvaluationType.MANUAL);
        String solution = "import java.util.Scanner;\n"
                + "public class Main { public static void main(String[] a) { Scanner sc = new Scanner(System.in); "
                + "int x = sc.nextInt(); int y = sc.nextInt(); System.out.println(x + y); } }";

        String body = mockMvc.perform(post("/api/v1/admin/practical-assessments/" + assessmentId + "/test-run")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminTestRunRequest(CodingLanguage.JAVA, solution))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AdminTestRunResponse result = objectMapper.readValue(body, AdminTestRunResponse.class);

        assertThat(result.testsTotal()).isEqualTo(2);
        assertThat(result.testsPassed()).isEqualTo(2);
        assertThat(result.testResults()).anyMatch(com.studen.practical.AdminExecutionTestResultResponse::hidden);
    }

    // --- SQL sandbox -------------------------------------------------------------------------------

    @Test
    void sql_correctQuery_passes() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-sql-ok-admin@example.com");
        String studentToken = registerAndGetToken("sbx-sql-ok-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox SQL Skill");
        UUID assessmentId = publishSqlProblem(adminToken, skillId, "SQL Top Scorers");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SaveAttemptRequest("SELECT name FROM students WHERE score > 75", null, null))))
                .andExpect(status().isOk());

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(1);
    }

    @Test
    void sql_incorrectQuery_reportedAsFailed() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-sql-wrong-admin@example.com");
        String studentToken = registerAndGetToken("sbx-sql-wrong-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox SQL Wrong Skill");
        UUID assessmentId = publishSqlProblem(adminToken, skillId, "SQL Top Scorers Wrong");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest("SELECT name FROM students", null, null))))
                .andExpect(status().isOk());

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        assertThat(result.testsPassed()).isEqualTo(0);
    }

    @Test
    void sql_multiStatementInjectionAttempt_isRejected() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-sql-inj-admin@example.com");
        String studentToken = registerAndGetToken("sbx-sql-inj-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox SQL Injection Skill");
        UUID assessmentId = publishSqlProblem(adminToken, skillId, "SQL Injection Attempt");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest(
                                "SELECT name FROM students; DROP TABLE students;", null, null))))
                .andExpect(status().isOk());

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("SECURITY_ERROR");
    }

    @Test
    void sql_cannotAccessApplicationTables() throws Exception {
        String adminToken = registerAdminAndGetToken("sbx-sql-isolation-admin@example.com");
        String studentToken = registerAndGetToken("sbx-sql-isolation-student@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox SQL Isolation Skill");
        UUID assessmentId = publishSqlProblem(adminToken, skillId, "SQL Isolation Attempt");
        PracticalAttemptResponse attempt = startAttempt(studentToken, assessmentId);
        mockMvc.perform(patch("/api/v1/practical-attempts/" + attempt.id())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveAttemptRequest("SELECT * FROM users", null, null))))
                .andExpect(status().isOk());

        RunResultResponse result = run(studentToken, attempt.id());
        assertThat(result.status().name()).isEqualTo("COMPLETED");
        // A fresh, seeded-only sandbox has no "users" table at all -- this must fail as a query
        // error, not succeed (which would mean it somehow reached a real/shared database).
        assertThat(result.publicTestResults().get(0).status().name()).isEqualTo("RUNTIME_ERROR");
    }

    // --- Concurrency ---------------------------------------------------------------------------

    // NOT_SUPPORTED, unlike every other test here: worker threads below issue their own MockMvc
    // requests on their own threads, which never inherit the class-level @Transactional's
    // thread-bound transaction. Under the normal wrapping, the admin/student/skill/assessment rows
    // created by setup are only ever visible on the main thread's still-uncommitted transaction --
    // every worker thread's own DB lookup (e.g. the JWT auth filter's user lookup) genuinely can't
    // see them yet and 401s. Running this one method outside any test transaction (setup commits
    // for real, same as production) fixes that; the random suffix keeps it safely rerunnable since
    // nothing here auto-rolls-back.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRuns_acrossMultipleAttempts_allCompleteSafely() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminToken = registerAdminAndGetToken("sbx-concurrency-admin-" + suffix + "@example.com");
        UUID skillId = createSkill(adminToken, "Sandbox Concurrency Skill " + suffix);
        UUID assessmentId = publishSumProblem(adminToken, skillId, "Java Sum Concurrency " + suffix, EvaluationType.MANUAL);

        int n = 6;
        String[] tokens = new String[n];
        UUID[] attemptIds = new UUID[n];
        for (int i = 0; i < n; i++) {
            tokens[i] = registerAndGetToken("sbx-concurrency-student-" + suffix + "-" + i + "@example.com");
            PracticalAttemptResponse attempt = startAttempt(tokens[i], assessmentId);
            attemptIds[i] = attempt.id();
            saveSubmission(tokens[i], attemptIds[i], "print(sum(map(int, input().split())))", CodingLanguage.PYTHON);
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger completed = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    RunResultResponse result = run(tokens[idx], attemptIds[idx]);
                    if ("COMPLETED".equals(result.status().name())) {
                        completed.incrementAndGet();
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        boolean finishedInTime = latch.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finishedInTime).isTrue();
        assertThat(completed.get()).isEqualTo(n);
    }
}
