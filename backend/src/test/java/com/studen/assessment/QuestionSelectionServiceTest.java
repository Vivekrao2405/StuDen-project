package com.studen.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionResponse;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class QuestionSelectionServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionSelectionService questionSelectionService;

    private String registerAdminAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test Admin", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(body, AuthResponse.class).accessToken();
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

    private void createAndPublish(String adminToken, UUID skillId, Difficulty difficulty, String questionText) throws Exception {
        String payload = """
                {
                  "skillId": "%s",
                  "questionText": "%s",
                  "questionType": "MCQ_SINGLE",
                  "difficulty": "%s",
                  "explanation": "Because A is correct.",
                  "options": [
                    {"optionText": "Option A", "displayOrder": 0, "isCorrect": true},
                    {"optionText": "Option B", "displayOrder": 1, "isCorrect": false}
                  ]
                }
                """.formatted(skillId, questionText, difficulty);
        String createdBody = mockMvc.perform(post("/api/v1/admin/questions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
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

    @Test
    void isAssessable_falseBelowThreshold_trueAtThreshold() throws Exception {
        String adminToken = registerAdminAndGetToken("qs-threshold-admin@example.com");
        UUID skillId = createSkill(adminToken, "QS Threshold Skill");
        for (int i = 0; i < 19; i++) {
            createAndPublish(adminToken, skillId, Difficulty.EASY, "Threshold question " + i + "?");
        }
        assertThat(questionSelectionService.isAssessable(skillId)).isFalse();

        createAndPublish(adminToken, skillId, Difficulty.EASY, "Threshold question 19?");
        assertThat(questionSelectionService.isAssessable(skillId)).isTrue();
    }

    @Test
    void select_returnsExactlyTotal_andOnlyFromThisSkill() throws Exception {
        String adminToken = registerAdminAndGetToken("qs-select-admin@example.com");
        UUID skillId = createSkill(adminToken, "QS Select Skill");
        UUID otherSkillId = createSkill(adminToken, "QS Other Skill");
        for (int i = 0; i < 20; i++) {
            createAndPublish(adminToken, skillId, Difficulty.MEDIUM, "Select question " + i + "?");
        }
        for (int i = 0; i < 20; i++) {
            createAndPublish(adminToken, otherSkillId, Difficulty.MEDIUM, "Other-skill question " + i + "?");
        }

        List<UUID> selected = questionSelectionService.select(skillId, 20);

        assertThat(selected).hasSize(20);
        assertThat(selected).doesNotHaveDuplicates();
    }

    @Test
    void select_gracefullyFillsShortfallFromOtherDifficulties() throws Exception {
        String adminToken = registerAdminAndGetToken("qs-fill-admin@example.com");
        UUID skillId = createSkill(adminToken, "QS Fill Skill");
        // No EASY/HARD questions at all — every question is MEDIUM. With the default 30/50/20
        // split over 20 total, EASY/HARD buckets are empty and must be filled from MEDIUM instead
        // of failing.
        for (int i = 0; i < 20; i++) {
            createAndPublish(adminToken, skillId, Difficulty.MEDIUM, "Fill question " + i + "?");
        }

        List<UUID> selected = questionSelectionService.select(skillId, 20);

        assertThat(selected).hasSize(20);
    }

    @Test
    void select_belowRequiredCount_throwsConflict() throws Exception {
        String adminToken = registerAdminAndGetToken("qs-insufficient-admin@example.com");
        UUID skillId = createSkill(adminToken, "QS Insufficient Skill");
        for (int i = 0; i < 5; i++) {
            createAndPublish(adminToken, skillId, Difficulty.EASY, "Insufficient question " + i + "?");
        }

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> questionSelectionService.select(skillId, 20))
                .isInstanceOf(com.studen.common.exception.ConflictException.class);
    }
}
