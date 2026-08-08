package com.studen.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    @Test
    void getCurrentUser_withValidJwt_returns200WithOwnProfile() throws Exception {
        String token = registerAndGetToken("me-valid@example.com");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me-valid@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getCurrentUser_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void getCurrentUser_withInvalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCurrentUser_withValidJwt_updatesOwnProfileOnly() throws Exception {
        String tokenA = registerAndGetToken("user-a@example.com");
        String tokenB = registerAndGetToken("user-b@example.com");

        String updatePayload = """
                {
                  "fullName": "Updated Name",
                  "phone": "+911234567890",
                  "profileImageUrl": "https://example.com/avatar.png"
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("+911234567890"))
                .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.email").value("user-a@example.com"));

        // User B's profile must remain untouched by User A's update
        String userBProfile = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UserResponse userB = objectMapper.readValue(userBProfile, UserResponse.class);
        assertThat(userB.fullName()).isEqualTo("Test User");
        assertThat(userB.email()).isEqualTo("user-b@example.com");
    }

    @Test
    void updateCurrentUser_withInvalidData_returns400() throws Exception {
        String token = registerAndGetToken("invalid-update@example.com");

        String invalidPayload = """
                {
                  "fullName": "",
                  "phone": "not-a-phone-number!!",
                  "profileImageUrl": "not-a-url"
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void updateCurrentUser_withoutJwt_returns401() throws Exception {
        String updatePayload = """
                {
                  "fullName": "No Auth",
                  "phone": null,
                  "profileImageUrl": null
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isUnauthorized());
    }
}
