package com.studen.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studen.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// AuthRateLimitFilter is a singleton shared across every @SpringBootTest in this JVM run, so its
// per-IP counter accumulates across the whole suite (all requests come from 127.0.0.1) rather
// than resetting per class. Its own logic is covered directly by AuthRateLimitFilterTest with a
// deliberately small threshold; this override just keeps this class's own register/login volume
// (and everyone else's, since the context — and this filter instance — is shared) from tripping
// it here.
@TestPropertySource(properties = "app.security.auth-rate-limit.max-requests=100000")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void register_withValidData_returns201WithTokenAndNoPasswordHash() throws Exception {
        RegisterRequest request = new RegisterRequest("Vivek Rao", "vivek.register@example.com", "SecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.fullName").value("Vivek Rao"))
                .andExpect(jsonPath("$.email").value("vivek.register@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("First User", "duplicate@example.com", "SecurePassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        RegisterRequest secondRequest = new RegisterRequest("Second User", "duplicate@example.com", "AnotherPassword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void register_withInvalidData_returns400() throws Exception {
        String invalidPayload = """
                {
                  "fullName": "",
                  "email": "not-an-email",
                  "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Login User", "login@example.com", "SecurePassword123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("login@example.com", "SecurePassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Login User", "badlogin@example.com", "SecurePassword123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest wrongPassword = new LoginRequest("badlogin@example.com", "WrongPassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        LoginRequest unknownEmail = new LoginRequest("doesnotexist@example.com", "SecurePassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownEmail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void login_afterRepeatedFailures_locksOutAndStillReturnsGenericMessage() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Brute Force Target",
                "bruteforce@example.com", "SecurePassword123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest wrongPassword = new LoginRequest("bruteforce@example.com", "WrongPassword123");

        // Five wrong-password attempts is the configured lockout threshold.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPassword)))
                    .andExpect(status().isUnauthorized());
        }

        // The account is now locked. Even the CORRECT password must be rejected with the exact
        // same generic message as a wrong one — an attacker (or the legitimate user) must not be
        // able to tell "locked" apart from "wrong credentials".
        LoginRequest correctPassword = new LoginRequest("bruteforce@example.com", "SecurePassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_lockout_isPerAccountAndDoesNotAffectOtherUsers() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Victim", "lockout-target@example.com", "SecurePassword123"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Bystander", "lockout-bystander@example.com", "SecurePassword123"))))
                .andExpect(status().isCreated());

        LoginRequest wrongPassword = new LoginRequest("lockout-target@example.com", "WrongPassword123");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPassword)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest bystanderCorrectPassword = new LoginRequest("lockout-bystander@example.com", "SecurePassword123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bystanderCorrectPassword)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Date issuedInThePast = new Date(System.currentTimeMillis() - 60_000);
        Date expiredOneMinuteAgo = new Date(System.currentTimeMillis() - 1_000);

        String expiredToken = Jwts.builder()
                .subject("expired-token-user@example.com")
                .claim("uid", java.util.UUID.randomUUID().toString())
                .issuedAt(issuedInThePast)
                .expiration(expiredOneMinuteAgo)
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_setsHttpOnlyRefreshCookie() throws Exception {
        Cookie cookie = registerAndCaptureRefreshCookie("refresh-cookie-shape@example.com");

        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertEquals("/api/v1/auth", cookie.getPath());
    }

    @Test
    void refresh_withValidCookie_issuesNewAccessTokenAndRotatesCookie() throws Exception {
        Cookie originalCookie = registerAndCaptureRefreshCookie("refresh-valid@example.com");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh").cookie(originalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("refresh-valid@example.com"))
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        Cookie rotatedCookie = refreshResult.getResponse().getCookie("studen_refresh_token");
        assertNotNull(rotatedCookie);
        assertNotEquals(originalCookie.getValue(), rotatedCookie.getValue());
    }

    @Test
    void refresh_withoutCookie_returns401WithSessionExpiredCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));
    }

    @Test
    void refresh_withInvalidCookie_returns401WithSessionExpiredCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("studen_refresh_token", "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));
    }

    @Test
    void refresh_withAlreadyRotatedCookie_returns401AndRevokesTheRotatedReplacement() throws Exception {
        Cookie originalCookie = registerAndCaptureRefreshCookie("refresh-reuse@example.com");

        MvcResult firstRefresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(originalCookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedCookie = firstRefresh.getResponse().getCookie("studen_refresh_token");

        // Replaying the now-revoked original token is treated as possible theft: it must fail...
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(originalCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));

        // ...and it must also have defensively revoked the legitimate rotated replacement, so even
        // the client that did everything right is forced back to a fresh login.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));
    }

    @Test
    void logout_revokesRefreshTokenAndClearsCookie() throws Exception {
        Cookie cookie = registerAndCaptureRefreshCookie("logout-target@example.com");

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie clearedCookie = logoutResult.getResponse().getCookie("studen_refresh_token");
        assertNotNull(clearedCookie);
        assertEquals(0, clearedCookie.getMaxAge());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));
    }

    @Test
    void logout_withoutCookie_stillReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private Cookie registerAndCaptureRefreshCookie(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Refresh Flow User", email, "SecurePassword123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        return result.getResponse().getCookie("studen_refresh_token");
    }
}
