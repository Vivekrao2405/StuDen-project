package com.studen.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separated from RefreshTokenService so the defensive "revoke every session for this user" write
 * runs in its own transaction (REQUIRES_NEW) and durably commits even though the caller
 * immediately throws afterward. Without this, the surrounding @Transactional rotate() method's
 * rollback — triggered by the very exception it throws to report the theft — would silently undo
 * the revocation it just performed, so a stolen/replayed refresh token would look defensively
 * handled but leave every session (including the illegitimate one) untouched.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }
}
