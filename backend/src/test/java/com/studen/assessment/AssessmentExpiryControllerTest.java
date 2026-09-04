package com.studen.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * A 0-minute time limit (so timeLimitSeconds=0) makes every assessment created in this class due
 * for expiry immediately — the backend-authoritative lazy expiry check (spec §23/§24) then fires
 * on the very next call, without needing an actual sleep/wait in the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.max-requests=100000",
        "app.assessment.time-limit-minutes=0"
})
class AssessmentExpiryControllerTest {

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

    private UUID createUniformSkill(String adminToken, String name, int count) throws Exception {
        String skillBody = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSkillRequest(name, "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID skillId = objectMapper.readValue(skillBody, SkillResponse.class).id();

        List<QuestionOptionRequest> options = List.of(new QuestionOptionRequest("Option A", 0, true),
                new QuestionOptionRequest("Option B", 1, false));
        for (int i = 0; i < count; i++) {
            QuestionRequest request = new QuestionRequest(skillId, null, name + " question " + i + "?",
                    QuestionType.MCQ_SINGLE, Difficulty.EASY, "why", null, null, options);
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
        }
        return skillId;
    }

    // Every test in this class registers a fresh student and starts exactly one assessment, so a
    // plain create (no GET-or-update idempotency needed) is enough — see
    // com.studen.portfolio.PortfolioSkillProfileService for why this is now required.
    private void createPortfolioWithSkill(String token, UUID skillId) throws Exception {
        PortfolioRequest request = new PortfolioRequest("Test Student", null, null, null, null, null, Set.of(skillId), null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private AssessmentDetailResponse startAssessment(String token, UUID skillId) throws Exception {
        createPortfolioWithSkill(token, skillId);
        String body = mockMvc.perform(post("/api/v1/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartAssessmentRequest(skillId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AssessmentDetailResponse.class);
    }

    @Test
    void get_afterDeadlinePassed_autoExpiresAndReturnsResult() throws Exception {
        String adminToken = registerAdminAndGetToken("as-expire-get-admin@example.com");
        String studentToken = registerAndGetToken("as-expire-get-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Expire Get Skill", 30);
        AssessmentDetailResponse started = startAssessment(studentToken, skillId);

        String body = mockMvc.perform(get("/api/v1/assessments/" + started.id())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessmentResultResponse result = objectMapper.readValue(body, AssessmentResultResponse.class);

        assertThat(result.status()).isEqualTo(AssessmentStatus.EXPIRED);
        assertThat(result.scorePercentage()).isEqualTo(0);
        assertThat(result.correctCount()).isEqualTo(0);
    }

    @Test
    void answer_afterDeadlinePassed_rejected() throws Exception {
        String adminToken = registerAdminAndGetToken("as-expire-answer-admin@example.com");
        String studentToken = registerAndGetToken("as-expire-answer-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Expire Answer Skill", 30);
        AssessmentDetailResponse started = startAssessment(studentToken, skillId);
        AssessmentQuestionView question = started.questions().get(0);
        UUID optionId = question.options().get(0).id();

        mockMvc.perform(patch("/api/v1/assessments/" + started.id() + "/questions/" + question.id() + "/answer")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(List.of(optionId)))))
                .andExpect(status().isConflict());
    }

    @Test
    void submit_afterDeadlinePassed_returnsExpiredNotSubmitted() throws Exception {
        String adminToken = registerAdminAndGetToken("as-expire-submit-admin@example.com");
        String studentToken = registerAndGetToken("as-expire-submit-student@example.com");
        UUID skillId = createUniformSkill(adminToken, "AS Expire Submit Skill", 30);
        AssessmentDetailResponse started = startAssessment(studentToken, skillId);

        String body = mockMvc.perform(post("/api/v1/assessments/" + started.id() + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AssessmentResultResponse result = objectMapper.readValue(body, AssessmentResultResponse.class);

        assertThat(result.status()).isEqualTo(AssessmentStatus.EXPIRED);
    }
}
