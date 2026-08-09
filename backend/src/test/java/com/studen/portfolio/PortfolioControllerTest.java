package com.studen.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import java.math.BigDecimal;
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
class PortfolioControllerTest {

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

    private PortfolioRequest samplePortfolioRequest(String headline) {
        return new PortfolioRequest(headline, "Passionate about building things", "3 years of freelance work",
                new BigDecimal("25.00"), "Within a day", "Hyderabad", true, "https://example.com/cover.png");
    }

    private PortfolioResponse createPortfolio(String token, String headline) throws Exception {
        String body = mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePortfolioRequest(headline))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, PortfolioResponse.class);
    }

    @Test
    void createPortfolio_withValidJwt_returns201WithGeneratedSlug() throws Exception {
        String token = registerAndGetToken("portfolio-create@example.com");

        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePortfolioRequest("Full Stack Developer"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.headline").value("Full Stack Developer"))
                .andExpect(jsonPath("$.publicSlug").value("test-user"))
                .andExpect(jsonPath("$.profileUrl").value("https://studen.app/u/test-user"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void createPortfolio_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePortfolioRequest("No Auth"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPortfolio_whenAlreadyExists_returns409() throws Exception {
        String token = registerAndGetToken("portfolio-duplicate@example.com");
        createPortfolio(token, "First Portfolio");

        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePortfolioRequest("Second Portfolio"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void createPortfolio_withInvalidData_returns400() throws Exception {
        String token = registerAndGetToken("portfolio-invalid@example.com");

        String invalidPayload = """
                {
                  "headline": "",
                  "coverImageUrl": "not-a-url"
                }
                """;

        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createPortfolio_generatesUniqueSlugsForSameFullName() throws Exception {
        String tokenA = registerAndGetToken("slug-a@example.com");
        String tokenB = registerAndGetToken("slug-b@example.com");

        PortfolioResponse portfolioA = createPortfolio(tokenA, "Designer A");
        PortfolioResponse portfolioB = createPortfolio(tokenB, "Designer B");

        assertThat(portfolioA.publicSlug()).isNotEqualTo(portfolioB.publicSlug());
    }

    @Test
    void getMyPortfolio_withoutPortfolio_returns404() throws Exception {
        String token = registerAndGetToken("no-portfolio@example.com");

        mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void getMyPortfolio_withValidJwt_returnsOwnPortfolio() throws Exception {
        String token = registerAndGetToken("portfolio-get@example.com");
        createPortfolio(token, "Video Editor");

        mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Video Editor"));
    }

    @Test
    void updateMyPortfolio_updatesOwnPortfolioOnly() throws Exception {
        String tokenA = registerAndGetToken("portfolio-update-a@example.com");
        String tokenB = registerAndGetToken("portfolio-update-b@example.com");
        createPortfolio(tokenA, "Original Headline A");
        createPortfolio(tokenB, "Original Headline B");

        mockMvc.perform(put("/api/v1/portfolio/me")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePortfolioRequest("Updated Headline A"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Updated Headline A"));

        mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Original Headline B"));
    }

    @Test
    void updateMyPortfolio_withoutJwt_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/portfolio/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samplePortfolioRequest("No Auth"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMyPortfolio_removesPortfolio() throws Exception {
        String token = registerAndGetToken("portfolio-delete@example.com");
        createPortfolio(token, "To Be Deleted");

        mockMvc.perform(delete("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portfolio/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMyPortfolio_withoutJwt_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolio/me"))
                .andExpect(status().isUnauthorized());
    }
}
