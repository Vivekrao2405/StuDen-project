package com.studen.certificate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.auth.AuthResponse;
import com.studen.auth.RegisterRequest;
import com.studen.portfolio.PortfolioRequest;
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
class CertificateControllerTest {

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
        PortfolioRequest request = new PortfolioRequest("Photographer", null, null, null, null, true, null, null);
        mockMvc.perform(post("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private CertificateRequest sampleCertificateRequest() {
        return new CertificateRequest("Adobe Certified Expert", "Adobe", java.time.LocalDate.of(2024, 6, 1),
                "https://example.com/certificate.pdf");
    }

    private String createCertificate(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/me/certificates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCertificateRequest())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readValue(body, CertificateResponse.class).id().toString();
    }

    @Test
    void createCertificate_withValidJwtAndPortfolio_returns201() throws Exception {
        String token = registerAndGetToken("certificate-create@example.com");
        createPortfolio(token);

        mockMvc.perform(post("/api/v1/users/me/certificates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCertificateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Adobe Certified Expert"));
    }

    @Test
    void createCertificate_withoutPortfolio_returns404() throws Exception {
        String token = registerAndGetToken("certificate-no-portfolio@example.com");

        mockMvc.perform(post("/api/v1/users/me/certificates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCertificateRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCertificate_withInvalidData_returns400() throws Exception {
        String token = registerAndGetToken("certificate-invalid@example.com");
        createPortfolio(token);

        String invalidPayload = """
                {
                  "title": "",
                  "certificateUrl": "not-a-url"
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/certificates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void createCertificate_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/certificates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCertificateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyCertificates_returnsOwnRecords() throws Exception {
        String token = registerAndGetToken("certificate-get@example.com");
        createPortfolio(token);
        createCertificate(token);

        mockMvc.perform(get("/api/v1/users/me/certificates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Adobe Certified Expert"));
    }

    @Test
    void updateCertificate_updatesOwnRecord() throws Exception {
        String token = registerAndGetToken("certificate-update@example.com");
        createPortfolio(token);
        String certificateId = createCertificate(token);

        CertificateRequest updated = new CertificateRequest("Adobe Certified Master", "Adobe",
                java.time.LocalDate.of(2025, 1, 1), "https://example.com/updated.pdf");

        mockMvc.perform(put("/api/v1/users/me/certificates/" + certificateId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Adobe Certified Master"));
    }

    @Test
    void updateCertificate_belongingToAnotherUser_returns404() throws Exception {
        String tokenA = registerAndGetToken("certificate-owner-a@example.com");
        String tokenB = registerAndGetToken("certificate-owner-b@example.com");
        createPortfolio(tokenA);
        createPortfolio(tokenB);
        String certificateId = createCertificate(tokenA);

        mockMvc.perform(put("/api/v1/users/me/certificates/" + certificateId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCertificateRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCertificate_removesOwnRecord() throws Exception {
        String token = registerAndGetToken("certificate-delete@example.com");
        createPortfolio(token);
        String certificateId = createCertificate(token);

        mockMvc.perform(delete("/api/v1/users/me/certificates/" + certificateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/certificates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteCertificate_belongingToAnotherUser_returns404() throws Exception {
        String tokenA = registerAndGetToken("certificate-delete-owner-a@example.com");
        String tokenB = registerAndGetToken("certificate-delete-owner-b@example.com");
        createPortfolio(tokenA);
        createPortfolio(tokenB);
        String certificateId = createCertificate(tokenA);

        mockMvc.perform(delete("/api/v1/users/me/certificates/" + certificateId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
