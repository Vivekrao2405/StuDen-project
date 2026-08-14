package com.studen.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * Security-critical: this endpoint's raw JSON must never contain an answer key. Assertions here
 * inspect the literal response body (not just DTO field access) per the explicit warning in the
 * Phase 7.1 spec (§37) and [[feedback-testing-and-tooling]] — a compiler-enforced DTO shape is not
 * itself proof the wire format is safe if something upstream ever changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class LearnerQuestionControllerTest {

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

    private QuestionResponse createAndPublish(String adminToken, UUID skillId, String questionText) throws Exception {
        String createdBody = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, questionText)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        QuestionResponse created = objectMapper.readValue(createdBody, QuestionResponse.class);

        mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String publishedBody = mockMvc.perform(post("/api/v1/admin/questions/" + created.id() + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(publishedBody, QuestionResponse.class);
    }

    @Test
    void list_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/skills/" + UUID.randomUUID() + "/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_asPlainStudent_isAllowed() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-learner-admin@example.com");
        String studentToken = registerAndGetToken("qb-learner-student@example.com");
        UUID skillId = createSkill(adminToken, "QB Learner Skill");
        createAndPublish(adminToken, skillId, "Learner-visible published question?");

        mockMvc.perform(get("/api/v1/skills/" + skillId + "/questions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void list_neverContainsIsCorrectOrExplanationInRawJson() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-learner-noleak-admin@example.com");
        String studentToken = registerAndGetToken("qb-learner-noleak-student@example.com");
        UUID skillId = createSkill(adminToken, "QB No Leak Skill");
        createAndPublish(adminToken, skillId, "This question must never leak its answer?");

        String rawJson = mockMvc.perform(get("/api/v1/skills/" + skillId + "/questions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(rawJson).doesNotContainIgnoringCase("isCorrect");
        assertThat(rawJson).doesNotContainIgnoringCase("explanation");
        assertThat(rawJson).doesNotContainIgnoringCase("createdBy");
        assertThat(rawJson).doesNotContainIgnoringCase("reviewedBy");
    }

    @Test
    void list_draftQuestion_neverAppears() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-learner-draft-admin@example.com");
        String studentToken = registerAndGetToken("qb-learner-draft-student@example.com");
        UUID skillId = createSkill(adminToken, "QB Draft Hidden Skill");
        mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcqSinglePayload(skillId, "Still a draft, must stay hidden?")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/skills/" + skillId + "/questions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_archivedQuestion_isExcluded() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-learner-archived-admin@example.com");
        String studentToken = registerAndGetToken("qb-learner-archived-student@example.com");
        UUID skillId = createSkill(adminToken, "QB Archived Hidden Skill");
        QuestionResponse published = createAndPublish(adminToken, skillId, "Published then archived question?");

        mockMvc.perform(post("/api/v1/admin/questions/" + published.id() + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/skills/" + skillId + "/questions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_publishedQuestion_appearsAndKeepsOptions() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-learner-visible-admin@example.com");
        String studentToken = registerAndGetToken("qb-learner-visible-student@example.com");
        UUID skillId = createSkill(adminToken, "QB Visible Skill");
        createAndPublish(adminToken, skillId, "Published and visible question?");

        String body = mockMvc.perform(get("/api/v1/skills/" + skillId + "/questions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Published and visible question?");
        assertThat(body).contains("Option A");
    }
}
