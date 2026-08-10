package com.studen.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks failed login attempts per email and applies a temporary, self-expiring lockout once a
 * threshold is crossed — never a permanent one. In-memory by design: this process only tracks
 * requests it personally handled, so if the backend is ever scaled to multiple instances behind
 * a load balancer, each instance enforces its own independent counter (an attacker spread across
 * instances gets a higher effective limit, not zero protection). If/when this app runs more than
 * one backend instance, replace the map here with a shared store (e.g. Redis) behind the same
 * isBlocked/recordFailure/recordSuccess surface — nothing above this class needs to change.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS_BEFORE_LOCKOUT = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final long BASE_LOCKOUT_MINUTES = 1;
    private static final long MAX_LOCKOUT_MINUTES = 30;

    private final Map<String, AttemptRecord> attemptsByEmail = new ConcurrentHashMap<>();

    public boolean isBlocked(String email) {
        AttemptRecord record = attemptsByEmail.get(normalize(email));
        return record != null && record.isLocked();
    }

    public void recordFailure(String email) {
        attemptsByEmail.compute(normalize(email), (key, existing) -> {
            AttemptRecord record = (existing == null || existing.isWindowExpired()) ? new AttemptRecord() : existing;
            record.registerFailure();
            return record;
        });
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptRecord {
        private int failureCount = 0;
        private Instant lastFailureAt;
        private Instant lockedUntil;

        void registerFailure() {
            failureCount++;
            lastFailureAt = Instant.now();
            if (failureCount >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
                long extra = failureCount - MAX_ATTEMPTS_BEFORE_LOCKOUT;
                long lockoutMinutes = Math.min(BASE_LOCKOUT_MINUTES << Math.min(extra, 5), MAX_LOCKOUT_MINUTES);
                lockedUntil = Instant.now().plus(Duration.ofMinutes(lockoutMinutes));
            }
        }

        boolean isLocked() {
            return lockedUntil != null && Instant.now().isBefore(lockedUntil);
        }

        boolean isWindowExpired() {
            return !isLocked() && lastFailureAt != null && Instant.now().isAfter(lastFailureAt.plus(ATTEMPT_WINDOW));
        }
    }
}
