package com.studen.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    @Test
    void isBlocked_withNoFailures_returnsFalse() {
        LoginAttemptService service = new LoginAttemptService();

        assertThat(service.isBlocked("student@example.com")).isFalse();
    }

    @Test
    void isBlocked_afterFewFailures_staysUnblocked() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 4; i++) {
            service.recordFailure("student@example.com");
        }

        assertThat(service.isBlocked("student@example.com")).isFalse();
    }

    @Test
    void isBlocked_afterThresholdFailures_becomesTrue() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            service.recordFailure("student@example.com");
        }

        assertThat(service.isBlocked("student@example.com")).isTrue();
    }

    @Test
    void isBlocked_isPerEmail_notGlobal() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            service.recordFailure("attacked@example.com");
        }

        assertThat(service.isBlocked("attacked@example.com")).isTrue();
        assertThat(service.isBlocked("someone-else@example.com")).isFalse();
    }

    @Test
    void isBlocked_isCaseInsensitiveOnEmail() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            service.recordFailure("Student@Example.com");
        }

        assertThat(service.isBlocked("student@example.com")).isTrue();
    }

    @Test
    void recordSuccess_clearsFailureHistoryAndUnblocks() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            service.recordFailure("student@example.com");
        }
        assertThat(service.isBlocked("student@example.com")).isTrue();

        // A locked account can't normally reach a successful login (login() checks isBlocked()
        // first), but this also verifies recordSuccess() doesn't merely no-op — it should fully
        // reset the counter, matching the "temporary, not permanent" lockout requirement once the
        // lockout window itself elapses in production.
        service.recordSuccess("student@example.com");

        assertThat(service.isBlocked("student@example.com")).isFalse();
    }

    @Test
    void recordFailure_belowThreshold_neverLocks() {
        LoginAttemptService service = new LoginAttemptService();

        for (int i = 0; i < 100; i++) {
            service.recordFailure("student-" + i + "@example.com");
        }

        // Each email only got one failure each — none should be locked.
        for (int i = 0; i < 100; i++) {
            assertThat(service.isBlocked("student-" + i + "@example.com")).isFalse();
        }
    }
}
