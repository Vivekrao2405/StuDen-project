package com.studen.aicoach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.practical.CodingLanguage;
import com.studen.practical.EvaluationType;
import com.studen.practical.PracticalAssessmentDetailResponse;
import com.studen.practical.PracticalAssessmentRequest;
import com.studen.practical.PracticalAttemptResponse;
import com.studen.practical.PracticalCodingLanguageRequest;
import com.studen.practical.PracticalQuestionRequest;
import com.studen.practical.PracticalTestCaseRequest;
import com.studen.practical.PracticalType;
import com.studen.practical.WorkspaceType;
import com.studen.questionbank.Difficulty;
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
 * HTTP-boundary checks for {@link AiCoachController} — ownership (404, never leaking existence)
 * and the real-Spring-context "unconfigured OpenAI" path (503, since no OPENAI_API_KEY is set
 * anywhere in the test environment). Business-logic coverage (hint progression, hidden-test
 * exclusion, rate limiting) lives in {@link AiCoachServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"app.security.auth-rate-limit.max-requests=100000", "app.execution.enabled=false"})
class AiCoachControllerTest {

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
                List.of(new PracticalTestCaseRequest("1 2", "3", false, 0, null)), null);
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

    @Test
    void requestAction_openAiUnconfiguredInTestEnvironment_returns503() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-ctrl-unavail-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-ctrl-unavail-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Controller Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Controller Unavailable Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID attemptQuestionId = attempt.questions().get(0).id();

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/questions/" + attemptQuestionId
                        + "/ai-coach")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"HINT\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_COACH_UNAVAILABLE"));
    }

    @Test
    void requestAction_anotherStudentsAttempt_returns404() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-ctrl-owner-admin@example.com");
        String ownerToken = registerAndGetToken("aicoach-ctrl-owner-student@example.com");
        String intruderToken = registerAndGetToken("aicoach-ctrl-intruder-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Controller Ownership Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Controller Ownership Problem");
        PracticalAttemptResponse attempt = startAttempt(ownerToken, skillId, assessmentId);
        UUID attemptQuestionId = attempt.questions().get(0).id();

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/questions/" + attemptQuestionId
                        + "/ai-coach")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"HINT\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id() + "/questions/" + attemptQuestionId
                        + "/ai-coach/history")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_beforeAnyInteraction_returnsEmptyList() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-ctrl-history-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-ctrl-history-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Controller History Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Controller History Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID attemptQuestionId = attempt.questions().get(0).id();

        mockMvc.perform(get("/api/v1/practical-attempts/" + attempt.id() + "/questions/" + attemptQuestionId
                        + "/ai-coach/history")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requestAction_missingActionType_returns400() throws Exception {
        String adminToken = registerAdminAndGetToken("aicoach-ctrl-validation-admin@example.com");
        String studentToken = registerAndGetToken("aicoach-ctrl-validation-student@example.com");
        UUID skillId = createSkill(adminToken, "AI Coach Controller Validation Skill");
        UUID assessmentId = publishCodingAssessment(adminToken, skillId, "Controller Validation Problem");
        PracticalAttemptResponse attempt = startAttempt(studentToken, skillId, assessmentId);
        UUID attemptQuestionId = attempt.questions().get(0).id();

        mockMvc.perform(post("/api/v1/practical-attempts/" + attempt.id() + "/questions/" + attemptQuestionId
                        + "/ai-coach")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
