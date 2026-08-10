package com.studen.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
import com.studen.portfolio.PortfolioResponse;
import com.studen.portfolio.StudentPortfolioRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentPortfolioRepository portfolioRepository;

    @Autowired
    private ProfileShareRepository profileShareRepository;

    @Autowired
    private ProfileCardRepository profileCardRepository;

    private String registerAndGetToken(String fullName, String email) throws Exception {
        RegisterRequest request = new RegisterRequest(fullName, email, "SecurePassword123");
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, AuthResponse.class).accessToken();
    }

    private PortfolioResponse createPortfolio(String token, String headline, String location) throws Exception {
        PortfolioRequest request = new PortfolioRequest(headline, "About me", "Summary",
                "1 day", location, true);
        String body = mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, PortfolioResponse.class);
    }

    @Test
    void getPublicProfile_withoutJwt_returns200() throws Exception {
        String token = registerAndGetToken("Public Viewer", "public-noauth@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Voice Over Artist", "Mumbai");

        mockMvc.perform(get("/api/v1/public/profiles/" + portfolio.publicSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(portfolio.publicSlug()))
                .andExpect(jsonPath("$.headline").value("Voice Over Artist"));
    }

    @Test
    void getPublicProfile_returnsOnlyPublicInformationAndNoSecurityData() throws Exception {
        String token = registerAndGetToken("Secure Person", "public-secure@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Data Entry Specialist", "Pune");

        mockMvc.perform(get("/api/v1/public/profiles/" + portfolio.publicSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Secure Person"))
                .andExpect(jsonPath("$.about").value("About me"))
                .andExpect(jsonPath("$.availability").value(true))
                .andExpect(jsonPath("$.education").isArray())
                .andExpect(jsonPath("$.certificates").isArray())
                .andExpect(jsonPath("$.skills").isArray())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void getPublicProfile_hidesLocationWhenShowContactIsFalse() throws Exception {
        String token = registerAndGetToken("Private Location", "public-private-location@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Fitness Trainer", "Bengaluru");

        ProfileShare share = profileShareRepository.findByPortfolioId(portfolio.id()).orElseThrow();
        share.setShowContact(false);
        profileShareRepository.save(share);

        mockMvc.perform(get("/api/v1/public/profiles/" + portfolio.publicSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").doesNotExist());
    }

    @Test
    void getPublicProfile_includesUploadedCoverImageUrl() throws Exception {
        String token = registerAndGetToken("Cover Image Person", "public-cover-image@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Photographer", "Goa");

        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/portfolio/me/cover-image")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/profiles/" + portfolio.publicSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverImageUrl").isNotEmpty());
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    @Test
    void getPublicProfile_withUnknownSlug_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/public/profiles/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyShareMetadata_withValidJwt_returnsSlugAndProfileUrl() throws Exception {
        String token = registerAndGetToken("Share Owner", "share-owner@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Calligrapher", "Delhi");

        mockMvc.perform(get("/api/v1/portfolio/me/share").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(portfolio.publicSlug()))
                .andExpect(jsonPath("$.profileUrl").value("https://studen.app/u/" + portfolio.publicSlug()))
                .andExpect(jsonPath("$.cardDownloadUrl").doesNotExist());
    }

    @Test
    void getMyShareMetadata_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/me/share"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyShareMetadata_withoutPortfolio_returns404() throws Exception {
        String token = registerAndGetToken("No Portfolio", "share-no-portfolio@example.com");

        mockMvc.perform(get("/api/v1/portfolio/me/share").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyShareMetadata_isIsolatedPerUser() throws Exception {
        String tokenA = registerAndGetToken("Share User A", "share-user-a@example.com");
        String tokenB = registerAndGetToken("Share User B", "share-user-b@example.com");
        PortfolioResponse portfolioA = createPortfolio(tokenA, "Musician", "Chennai");
        PortfolioResponse portfolioB = createPortfolio(tokenB, "Dancer", "Kolkata");

        mockMvc.perform(get("/api/v1/portfolio/me/share").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(portfolioA.publicSlug()));

        mockMvc.perform(get("/api/v1/portfolio/me/share").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(portfolioB.publicSlug()));

        assertThat(portfolioA.publicSlug()).isNotEqualTo(portfolioB.publicSlug());
    }

    @Test
    void profileCard_relationshipIsReflectedInShareMetadata() throws Exception {
        String token = registerAndGetToken("Card Owner", "card-owner@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Makeup Artist", "Jaipur");

        ProfileShare share = profileShareRepository.findByPortfolioId(portfolio.id()).orElseThrow();
        ProfileCard card = new ProfileCard(share, ProfileCardType.IMAGE, "https://example.com/card.png");
        profileCardRepository.save(card);

        mockMvc.perform(get("/api/v1/portfolio/me/share").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardDownloadUrl").value("https://example.com/card.png"));
    }

    @Test
    void profileShare_isAutomaticallyCreatedWithPortfolio() throws Exception {
        String token = registerAndGetToken("Auto Share", "auto-share@example.com");
        PortfolioResponse portfolio = createPortfolio(token, "Translator", "Jaipur");

        UUID portfolioId = portfolio.id();
        assertThat(profileShareRepository.findByPortfolioId(portfolioId)).isPresent();
    }
}
