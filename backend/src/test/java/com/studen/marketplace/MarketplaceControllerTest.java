package com.studen.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.AvailabilityOption;
import com.studen.portfolio.PortfolioRequest;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
class MarketplaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private StudentPortfolioRepository studentPortfolioRepository;

    @Autowired
    private ServiceListingRepository serviceListingRepository;

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

    private UUID skillIdByName(String displayName) {
        return skillRepository.findByNormalizedName(displayName.toLowerCase(java.util.Locale.ROOT))
                .map(Skill::getId)
                .orElseThrow(() -> new IllegalStateException("Seed skill not found: " + displayName));
    }

    private Skill skillByName(String displayName) {
        return skillRepository.findByNormalizedName(displayName.toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException("Seed skill not found: " + displayName));
    }

    /** Creates a real user + portfolio via the actual registration/portfolio-creation flow (not
     * hand-built entities), matching how this data is created in production. Full name is fixed
     * at registration ("Test User") — tests search on headline text instead, which is fully
     * controllable per test. */
    private StudentPortfolio createStudent(String email, String headline, String location, boolean available,
            List<String> skillNames) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Test User", email, "SecurePassword123");
        String registerBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        AuthResponse auth = objectMapper.readValue(registerBody, AuthResponse.class);

        Set<UUID> skillIds = skillNames.stream().map(this::skillIdByName).collect(Collectors.toSet());
        PortfolioRequest request = new PortfolioRequest(headline, "Bio for " + headline, null, null, location,
                available, skillIds, Set.of(AvailabilityOption.FREELANCE_PROJECTS));

        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        return studentPortfolioRepository.findByUserId(auth.id()).orElseThrow();
    }

    private ServiceListing createService(StudentPortfolio portfolio, String title, MarketplaceCategory category,
            String location, List<String> skillNames) {
        ServiceListing listing = new ServiceListing(portfolio, title, category);
        listing.setDescription("Description for " + title);
        listing.setLocation(location);
        listing.setSkills(skillNames.stream().map(this::skillByName).collect(Collectors.toSet()));
        return serviceListingRepository.save(listing);
    }

    // --- Auth -------------------------------------------------------------------------------

    @Test
    void search_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/marketplace"))
                .andExpect(status().isUnauthorized());
    }

    // --- Search by text -----------------------------------------------------------------------

    @Test
    void search_byHeadline_findsMatchingStudent() throws Exception {
        String token = registerAndGetToken("mp-headline@example.com");
        createStudent("mp-headline-student@example.com", "Unique Falconry Instructor Headline",
                "Hyderabad", true, List.of("React"));

        mockMvc.perform(get("/api/v1/marketplace").param("q", "Falconry Instructor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("STUDENT"))
                .andExpect(jsonPath("$.content[0].headline").value("Unique Falconry Instructor Headline"));
    }

    @Test
    void search_bySkillName_findsBothStudentsAndServices() throws Exception {
        String token = registerAndGetToken("mp-skill@example.com");
        StudentPortfolio portfolio = createStudent("mp-skill-student@example.com", "Power BI expert",
                "Hyderabad", true, List.of("Power BI"));
        createService(portfolio, "Power BI Dashboard Build", MarketplaceCategory.DATA_ANALYTICS, "Hyderabad",
                List.of("Power BI"));

        String body = mockMvc.perform(get("/api/v1/marketplace").param("q", "power bi")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"type\":\"STUDENT\"");
        assertThat(body).contains("\"type\":\"SERVICE\"");
        assertThat(body).contains("Power BI Dashboard Build");
    }

    @Test
    void search_byServiceTitle_findsService() throws Exception {
        String token = registerAndGetToken("mp-title@example.com");
        StudentPortfolio portfolio = createStudent("mp-title-student@example.com", "Designer",
                "Mumbai", true, List.of("Figma"));
        createService(portfolio, "Unique Logo Concept Package", MarketplaceCategory.DESIGN_CREATIVE, "Mumbai",
                List.of("Figma"));

        mockMvc.perform(get("/api/v1/marketplace").param("q", "Unique Logo Concept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Unique Logo Concept Package"));
    }

    // --- Filters ------------------------------------------------------------------------------

    @Test
    void search_categoryFilter_onlyReturnsMatchingCategoryStudents() throws Exception {
        String token = registerAndGetToken("mp-category@example.com");
        createStudent("mp-category-tech@example.com", "Marketplace Category Tech Test", "Pune", true,
                List.of("React"));
        createStudent("mp-category-design@example.com", "Marketplace Category Design Test", "Pune",
                true, List.of("Figma"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Marketplace Category")
                        .param("category", "TECHNOLOGY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].headline", hasItem("Marketplace Category Tech Test")))
                .andExpect(jsonPath("$.content[*].headline")
                        .value(org.hamcrest.Matchers.not(hasItem("Marketplace Category Design Test"))));
    }

    @Test
    void search_locationFilter_filtersByLocation() throws Exception {
        String token = registerAndGetToken("mp-location@example.com");
        createStudent("mp-location-a@example.com", "Location Filter Test A", "Chennai", true,
                List.of("React"));
        createStudent("mp-location-b@example.com", "Location Filter Test B", "Delhi", true,
                List.of("React"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Location Filter Test")
                        .param("location", "Chennai")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].headline", hasItem("Location Filter Test A")))
                .andExpect(jsonPath("$.content[*].headline")
                        .value(org.hamcrest.Matchers.not(hasItem("Location Filter Test B"))));
    }

    @Test
    void search_availabilityFilter_availableOnly() throws Exception {
        String token = registerAndGetToken("mp-avail@example.com");
        createStudent("mp-avail-yes@example.com", "Availability Filter Test Yes", "Hyderabad", true,
                List.of("React"));
        createStudent("mp-avail-no@example.com", "Availability Filter Test No", "Hyderabad", false,
                List.of("React"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Availability Filter Test")
                        .param("availability", "AVAILABLE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].headline", hasItem("Availability Filter Test Yes")))
                .andExpect(jsonPath("$.content[*].headline")
                        .value(org.hamcrest.Matchers.not(hasItem("Availability Filter Test No"))));
    }

    @Test
    void search_availabilityFilter_notAvailableOnly() throws Exception {
        String token = registerAndGetToken("mp-avail2@example.com");
        createStudent("mp-avail2-yes@example.com", "Availability2 Filter Test Yes", "Hyderabad", true,
                List.of("React"));
        createStudent("mp-avail2-no@example.com", "Availability2 Filter Test No", "Hyderabad", false,
                List.of("React"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Availability2 Filter Test")
                        .param("availability", "NOT_AVAILABLE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].headline", hasItem("Availability2 Filter Test No")))
                .andExpect(jsonPath("$.content[*].headline")
                        .value(org.hamcrest.Matchers.not(hasItem("Availability2 Filter Test Yes"))));
    }

    @Test
    void search_skillFilter_exactMatch() throws Exception {
        String token = registerAndGetToken("mp-skillfilter@example.com");
        createStudent("mp-skillfilter-a@example.com", "Skill Filter Test A", "Hyderabad", true,
                List.of("React", "TypeScript"));
        createStudent("mp-skillfilter-b@example.com", "Skill Filter Test B", "Hyderabad", true,
                List.of("Python"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Skill Filter Test")
                        .param("skill", "TypeScript")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].headline", hasItem("Skill Filter Test A")))
                .andExpect(jsonPath("$.content[*].headline")
                        .value(org.hamcrest.Matchers.not(hasItem("Skill Filter Test B"))));
    }

    // --- Sorting / pagination -------------------------------------------------------------------

    @Test
    void search_sortNewest_ordersMostRecentFirst() throws Exception {
        String token = registerAndGetToken("mp-sort@example.com");
        createStudent("mp-sort-first@example.com", "Sort Order Test First", "Hyderabad", true,
                List.of("React"));
        createStudent("mp-sort-second@example.com", "Sort Order Test Second", "Hyderabad", true,
                List.of("React"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Sort Order Test")
                        .param("sort", "newest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].headline").value("Sort Order Test Second"))
                .andExpect(jsonPath("$.content[1].headline").value("Sort Order Test First"));
    }

    @Test
    void search_pagination_returnsCorrectPageMetadata() throws Exception {
        String token = registerAndGetToken("mp-page@example.com");
        createStudent("mp-page-a@example.com", "Pagination Test Alpha", "Hyderabad", true,
                List.of("React"));
        createStudent("mp-page-b@example.com", "Pagination Test Beta", "Hyderabad", true,
                List.of("React"));
        createStudent("mp-page-c@example.com", "Pagination Test Gamma", "Hyderabad", true,
                List.of("React"));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Pagination Test")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/marketplace")
                        .param("q", "Pagination Test")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    // --- Empty / invalid input ------------------------------------------------------------------

    @Test
    void search_noMatches_returnsEmptyContent() throws Exception {
        String token = registerAndGetToken("mp-empty@example.com");

        mockMvc.perform(get("/api/v1/marketplace").param("q", "zzz-no-such-marketplace-match-zzz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void search_invalidCategory_returns400() throws Exception {
        String token = registerAndGetToken("mp-badcat@example.com");

        mockMvc.perform(get("/api/v1/marketplace").param("category", "NOT_A_REAL_CATEGORY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void search_invalidAvailability_returns400() throws Exception {
        String token = registerAndGetToken("mp-badavail@example.com");

        mockMvc.perform(get("/api/v1/marketplace").param("availability", "MAYBE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void search_nonNumericSize_returns400() throws Exception {
        String token = registerAndGetToken("mp-badsize@example.com");

        mockMvc.perform(get("/api/v1/marketplace").param("size", "not-a-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // --- Privacy ----------------------------------------------------------------------------

    @Test
    void search_responseNeverContainsEmailOrPassword() throws Exception {
        String token = registerAndGetToken("mp-privacy@example.com");
        createStudent("mp-privacy-student@example.com", "Privacy Check Headline", "Hyderabad", true,
                List.of("React"));

        String body = mockMvc.perform(get("/api/v1/marketplace").param("q", "Privacy Check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("mp-privacy-student@example.com");
        assertThat(body).doesNotContainIgnoringCase("password");
        assertThat(body).doesNotContainIgnoringCase("\"email\"");
    }
}
