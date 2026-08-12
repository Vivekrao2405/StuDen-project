package com.studen.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import java.util.List;
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
// See AuthControllerTest for why: AuthRateLimitFilter's per-IP counter is shared (and
// accumulates) across every @SpringBootTest in this JVM run.
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
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

    // --- Universal (non-tech) skill domains -------------------------------------------------

    @Test
    void search_photography_returnsNonTechCreativeResults() throws Exception {
        String token = registerAndGetToken("skill-photo@example.com");

        String body = mockMvc.perform(get("/api/v1/skills/search").param("q", "pho")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<SkillResponse> results = List.of(objectMapper.readValue(body, SkillResponse[].class));
        assertThat(results).extracting(SkillResponse::name).contains("Photography", "Photo Editing");
        assertThat(results).extracting(SkillResponse::category).contains("Photography & Video");
    }

    @Test
    void search_marketing_returnsBusinessResults() throws Exception {
        String token = registerAndGetToken("skill-marketing@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "mar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Marketing", "Market Research")));
    }

    @Test
    void search_football_returnsSportsResults() throws Exception {
        String token = registerAndGetToken("skill-football@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "foot")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Football", "Football Coaching")))
                .andExpect(jsonPath("$[*].category", hasItems("Sports & Fitness")));
    }

    @Test
    void search_guitar_returnsArtsResults() throws Exception {
        String token = registerAndGetToken("skill-guitar@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "guit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Guitar", "Guitar Performance")));
    }

    @Test
    void search_byCategoryName_surfacesSkillsInThatCategory() throws Exception {
        String token = registerAndGetToken("skill-category-search@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "leadership")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Leadership")));
    }

    @Test
    void search_genericSkill_returnsLucideIconType() throws Exception {
        String token = registerAndGetToken("skill-lucide-type@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "photography")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Photography"))
                .andExpect(jsonPath("$[0].iconType").value("LUCIDE"))
                .andExpect(jsonPath("$[0].iconSlug").value("Camera"));
    }

    @Test
    void search_brandSkill_returnsBrandIconType() throws Exception {
        String token = registerAndGetToken("skill-brand-type@example.com");

        mockMvc.perform(get("/api/v1/skills/search").param("q", "react")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("React"))
                .andExpect(jsonPath("$[0].iconType").value("BRAND"));
    }

    // --- Categories ---------------------------------------------------------------------------

    @Test
    void getCategories_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/skills/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCategories_returnsDistinctSortedListSpanningNonTechDomains() throws Exception {
        String token = registerAndGetToken("skill-categories@example.com");

        String body = mockMvc.perform(get("/api/v1/skills/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> categories = List.of(objectMapper.readValue(body, String[].class));
        assertThat(categories).doesNotHaveDuplicates();
        // Postgres orders this with its (locale-aware, case-insensitive-ish) default collation,
        // not Java's case-sensitive String::compareTo — e.g. "Academic" legitimately sorts before
        // "AI/ML" in the DB even though 'c' > 'I' in strict ASCII.
        assertThat(categories).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(categories).contains("Sports & Fitness", "Arts & Performance", "Business",
                "Languages", "Practical Skills", "Career Skills", "Frontend", "Design");
    }

    // --- Custom skills --------------------------------------------------------------------------

    @Test
    void createCustomSkill_withoutJwt_returns401() throws Exception {
        CreateSkillRequest request = new CreateSkillRequest("Underwater Basket Weaving", "Practical Skills");

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCustomSkill_withNewName_createsSkillWithGenericIcon() throws Exception {
        String token = registerAndGetToken("skill-custom-create@example.com");
        CreateSkillRequest request = new CreateSkillRequest("Falconry", "Practical Skills");

        mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Falconry"))
                .andExpect(jsonPath("$.category").value("Practical Skills"))
                .andExpect(jsonPath("$.iconType").value("LUCIDE"))
                .andExpect(jsonPath("$.iconSlug").value("Sparkles"));
    }

    @Test
    void createCustomSkill_calledTwiceWithDifferentCasing_returnsSameSkill() throws Exception {
        String token = registerAndGetToken("skill-custom-dedup@example.com");

        String firstBody = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSkillRequest("Beekeeping", "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        SkillResponse first = objectMapper.readValue(firstBody, SkillResponse.class);

        String secondBody = mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSkillRequest("  BEEKEEPING  ", "Practical Skills"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        SkillResponse second = objectMapper.readValue(secondBody, SkillResponse.class);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.name()).isEqualTo("Beekeeping");
    }

    @Test
    void createCustomSkill_thenFindableViaSearch() throws Exception {
        String token = registerAndGetToken("skill-custom-searchable@example.com");

        mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSkillRequest("Origami", "Arts & Performance"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/skills/search").param("q", "origami")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Origami"));
    }

    @Test
    void createCustomSkill_withBlankName_returns400() throws Exception {
        String token = registerAndGetToken("skill-custom-invalid@example.com");

        String invalidPayload = """
                {
                  "name": "",
                  "category": "Practical Skills"
                }
                """;

        mockMvc.perform(post("/api/v1/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
