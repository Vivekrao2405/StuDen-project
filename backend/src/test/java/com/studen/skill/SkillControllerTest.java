package com.studen.skill;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SkillControllerTest {

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
    void search_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/skills/search").param("q", "react"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_isCaseInsensitiveAndRanksExactMatchFirst() throws Exception {
        String token = registerAndGetToken("skill-react@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "REACT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("React"))
                .andExpect(jsonPath("$[1].name").value("React Native"));
    }

    @Test
    void search_ranksPrefixMatchesBeforeAliasPartialMatches() throws Exception {
        String token = registerAndGetToken("skill-java@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "java")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[1].name").value("JavaScript"));
    }

    @Test
    void search_matchesByAlias() throws Exception {
        String token = registerAndGetToken("skill-js@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "js")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("JavaScript"));
    }

    @Test
    void search_matchesPartialWithinName() throws Exception {
        String token = registerAndGetToken("skill-power@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "power")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Power BI"));
    }

    @Test
    void search_returnsIconSlugAndCategory() throws Exception {
        String token = registerAndGetToken("skill-figma@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "figma")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Figma"))
                .andExpect(jsonPath("$[0].category").value("Design"))
                .andExpect(jsonPath("$[0].iconSlug").value("figma"));
    }

    @Test
    void search_withBlankQuery_returnsEmptyList() throws Exception {
        String token = registerAndGetToken("skill-blank@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void search_withNoMatches_returnsEmptyList() throws Exception {
        String token = registerAndGetToken("skill-nomatch@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "zzz-not-a-real-skill")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
