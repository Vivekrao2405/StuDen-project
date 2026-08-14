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
class AdminTopicControllerTest {

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

    @Test
    void create_asStudent_returns403() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-topic-owner@example.com");
        String studentToken = registerAndGetToken("qb-topic-student@example.com");
        UUID skillId = createSkill(adminToken, "QB Topic Skill Guard");

        mockMvc.perform(post("/api/v1/admin/topics")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\": \"" + skillId + "\", \"name\": \"Should be forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_thenListBySkill_returnsIt() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-topic-create@example.com");
        UUID skillId = createSkill(adminToken, "QB Topic Skill Create");

        mockMvc.perform(post("/api/v1/admin/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\": \"" + skillId + "\", \"name\": \"Functions\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/v1/admin/topics").param("skillId", skillId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<TopicResponse> topics = List.of(objectMapper.readValue(body, TopicResponse[].class));
        assertThat(topics).extracting(TopicResponse::name).contains("Functions");
    }

    @Test
    void create_calledTwiceWithSameNameDifferentCasing_returnsSameTopic() throws Exception {
        String adminToken = registerAdminAndGetToken("qb-topic-dedup@example.com");
        UUID skillId = createSkill(adminToken, "QB Topic Skill Dedup");

        String firstBody = mockMvc.perform(post("/api/v1/admin/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\": \"" + skillId + "\", \"name\": \"OOP\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        TopicResponse first = objectMapper.readValue(firstBody, TopicResponse.class);

        String secondBody = mockMvc.perform(post("/api/v1/admin/topics")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\": \"" + skillId + "\", \"name\": \"  oop  \"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        TopicResponse second = objectMapper.readValue(secondBody, TopicResponse.class);

        assertThat(second.id()).isEqualTo(first.id());
    }
}
