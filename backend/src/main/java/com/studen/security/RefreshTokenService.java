package com.studen.security;

import com.studen.common.exception.InvalidRefreshTokenException;
import com.studen.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
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

    // How long a just-rotated token is still tolerated as a benign race rather than treated as
    // theft. Two concurrent requests legitimately presenting the SAME still-valid refresh cookie
    // is a completely normal occurrence — e.g. two open tabs (or a tab plus the installed PWA)
    // whose access tokens expire around the same moment, each independently firing its own
    // POST /auth/refresh before either has observed the other's Set-Cookie. Only a replay outside
    // this window, or one whose replacement chain is itself already broken, is treated as a real
    // reuse/theft signal.
    private static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(30);

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRevoker revoker;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties, RefreshTokenRevoker revoker) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
        this.revoker = revoker;
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
     * token/row takes its place. A token that's already revoked or expired isn't automatically
     * theft — see resolveRotationTarget() — but once a presentation is deemed genuinely invalid,
     * every session for that user is revoked defensively, forcing a fresh, verified login rather
     * than silently trusting the replay.
     */
    @Transactional
    public IssuedRefreshToken rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        RefreshToken tokenToRotate = resolveRotationTarget(existing);

        User user = tokenToRotate.getUser();
        if (!user.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        IssuedRefreshToken issued = issue(user, userAgent, ipAddress);
        tokenToRotate.setRevokedAt(Instant.now());
        tokenToRotate.setReplacedByHash(hash(issued.rawToken()));
        repository.save(tokenToRotate);

        return issued;
    }

    /**
     * Decides which row a rotate() call actually rotates. A token still live (not revoked, not
     * expired) is rotated directly — the normal case. A token that's already revoked is not
     * automatically theft: two concurrent requests legitimately presenting the same still-valid
     * cookie (two open tabs/devices racing a refresh) is completely normal, and the "loser" of
     * that race would otherwise be logged out for a reason the user could never explain. If the
     * presented token was rotated moments ago (within REUSE_GRACE_PERIOD) and its replacement is
     * still live, this is that benign race — ride forward onto the replacement instead. Anything
     * else (outside the grace window, expired outright, or a replacement chain that's itself
     * already broken) is treated as real reuse/theft and defensively revokes every session for
     * the user.
     */
    private RefreshToken resolveRotationTarget(RefreshToken existing) {
        boolean expired = existing.getExpiresAt().isBefore(Instant.now());
        if (existing.getRevokedAt() == null && !expired) {
            return existing;
        }

        if (existing.getRevokedAt() != null) {
            boolean withinGrace = existing.getRevokedAt().isAfter(Instant.now().minus(REUSE_GRACE_PERIOD));
            RefreshToken replacement = withinGrace && existing.getReplacedByHash() != null
                    ? repository.findByTokenHash(existing.getReplacedByHash()).orElse(null)
                    : null;
            boolean chainStillLive = replacement != null
                    && replacement.getRevokedAt() == null
                    && replacement.getExpiresAt().isAfter(Instant.now());
            if (chainStillLive) {
                return replacement;
            }
        }

        revoker.revokeAllForUser(existing.getUser().getId());
        throw new InvalidRefreshTokenException();
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
