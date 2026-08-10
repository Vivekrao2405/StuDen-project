package com.studen.education;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
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
class EducationControllerTest {

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

    private void createPortfolio(String token) throws Exception {
        PortfolioRequest request = new PortfolioRequest("Tutor", null, null, new BigDecimal("10.00"), null, null, true);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private EducationRequest sampleEducationRequest() {
        return new EducationRequest("B.Tech Computer Science", "Computer Science", "IIT Hyderabad", 2020, 2024, false);
    }

    private String createEducation(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/me/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEducationRequest())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, EducationResponse.class).id().toString();
    }

    @Test
    void createEducation_withValidJwtAndPortfolio_returns201() throws Exception {
        String token = registerAndGetToken("education-create@example.com");
        createPortfolio(token);

        mockMvc.perform(post("/api/v1/users/me/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEducationRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.degree").value("B.Tech Computer Science"))
                .andExpect(jsonPath("$.institution").value("IIT Hyderabad"));
    }

    @Test
    void createEducation_withoutPortfolio_returns404() throws Exception {
        String token = registerAndGetToken("education-no-portfolio@example.com");

        mockMvc.perform(post("/api/v1/users/me/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEducationRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEducation_withEndYearBeforeStartYear_returns400() throws Exception {
        String token = registerAndGetToken("education-invalid-years@example.com");
        createPortfolio(token);

        EducationRequest invalid = new EducationRequest("B.Tech", "CS", "IIT", 2022, 2020, false);

        mockMvc.perform(post("/api/v1/users/me/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createEducation_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEducationRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyEducation_returnsOwnRecords() throws Exception {
        String token = registerAndGetToken("education-get@example.com");
        createPortfolio(token);
        createEducation(token);

        mockMvc.perform(get("/api/v1/users/me/education").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].institution").value("IIT Hyderabad"));
    }

    @Test
    void updateEducation_updatesOwnRecord() throws Exception {
        String token = registerAndGetToken("education-update@example.com");
        createPortfolio(token);
        String educationId = createEducation(token);

        EducationRequest updated = new EducationRequest("M.Tech Computer Science", "Computer Science", "IIT Hyderabad",
                2020, 2026, false);

        mockMvc.perform(put("/api/v1/users/me/education/" + educationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degree").value("M.Tech Computer Science"));
    }

    @Test
    void updateEducation_belongingToAnotherUser_returns404() throws Exception {
        String tokenA = registerAndGetToken("education-owner-a@example.com");
        String tokenB = registerAndGetToken("education-owner-b@example.com");
        createPortfolio(tokenA);
        createPortfolio(tokenB);
        String educationId = createEducation(tokenA);

        mockMvc.perform(put("/api/v1/users/me/education/" + educationId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleEducationRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEducation_removesOwnRecord() throws Exception {
        String token = registerAndGetToken("education-delete@example.com");
        createPortfolio(token);
        String educationId = createEducation(token);

        mockMvc.perform(delete("/api/v1/users/me/education/" + educationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/education").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteEducation_belongingToAnotherUser_returns404() throws Exception {
        String tokenA = registerAndGetToken("education-delete-owner-a@example.com");
        String tokenB = registerAndGetToken("education-delete-owner-b@example.com");
        createPortfolio(tokenA);
        createPortfolio(tokenB);
        String educationId = createEducation(tokenA);

        mockMvc.perform(delete("/api/v1/users/me/education/" + educationId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
