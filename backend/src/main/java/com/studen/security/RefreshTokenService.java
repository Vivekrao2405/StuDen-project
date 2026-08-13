package com.studen.security;

import com.studen.common.exception.InvalidRefreshTokenException;
import com.studen.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes long-lived refresh tokens backing the access-token-renewal flow.
 * Only a SHA-256 hash of each raw token is ever persisted — the raw value exists only in the
 * HttpOnly cookie sent to the browser and is never stored, logged, or returned in a response body.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 64;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedRefreshToken issue(User user, String userAgent, String ipAddress) {
        String rawToken = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()));
        entity.setUserAgent(truncate(userAgent));
        entity.setIpAddress(ipAddress);
        repository.save(entity);

        return new IssuedRefreshToken(user, rawToken);
    }

    /**
     * Validates and rotates a presented refresh token: the old row is revoked and a brand-new
     * token/row takes its place. Presenting a token that is already revoked or expired is treated
     * as a possible sign of theft (a stolen token being replayed after the legitimate client
     * already rotated past it) — every session for that user is revoked defensively, forcing a
     * fresh, verified login rather than silently trusting the replay.
     */
    @Transactional
    public IssuedRefreshToken rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.getRevokedAt() != null || existing.getExpiresAt().isBefore(Instant.now())) {
            repository.revokeAllForUser(existing.getUser().getId(), Instant.now());
            throw new InvalidRefreshTokenException();
        }

        User user = existing.getUser();
        if (!user.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        existing.setRevokedAt(Instant.now());
        repository.save(existing);

        return issue(user, userAgent, ipAddress);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            repository.save(token);
        });
    }

    public long refreshExpirationMs() {
        return jwtProperties.getRefreshExpirationMs();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_USER_AGENT_LENGTH ? value.substring(0, MAX_USER_AGENT_LENGTH) : value;
    }

    public record IssuedRefreshToken(User user, String rawToken) {
    }
}
