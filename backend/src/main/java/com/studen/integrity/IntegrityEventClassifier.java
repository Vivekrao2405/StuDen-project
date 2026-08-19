package com.studen.integrity;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * The only place in the codebase that decides an integrity event's severity -- always
 * server-side, always deterministic, never influenced by anything the client sent except the
 * event type itself and (for away-duration) two already-validated {@code occurredAt} timestamps.
 * Used by {@link IntegrityScoringService}'s single ordered pass over an attempt's event log.
 */
@Component
public class IntegrityEventClassifier {

    private final IntegrityScoringProperties properties;

    public IntegrityEventClassifier(IntegrityScoringProperties properties) {
        this.properties = properties;
    }

    // TAB_HIDDEN/WINDOW_BLUR whose matching TAB_VISIBLE/WINDOW_FOCUS never arrived (attempt
    // expired or the tab was simply closed) -- duration is unknown, so this is a flat MEDIUM
    // rather than guessing.
    public IntegritySeverity classifyUnresolvedAway() {
        return IntegritySeverity.MEDIUM;
    }

    // occurrenceIndex is 1 for the first paired away period this attempt, 2 for the second, etc.
    public IntegritySeverity classifyAway(Duration duration, int occurrenceIndex) {
        IntegritySeverity byDuration;
        long seconds = duration.getSeconds();
        if (seconds < properties.getAwayShortMaxSeconds()) {
            byDuration = IntegritySeverity.LOW;
        } else if (seconds < properties.getAwayMediumMaxSeconds()) {
            byDuration = IntegritySeverity.MEDIUM;
        } else {
            byDuration = IntegritySeverity.HIGH;
        }
        if (occurrenceIndex > properties.getAwayEscalateAfterCount()) {
            return escalate(byDuration);
        }
        return byDuration;
    }

    // COPY_ATTEMPT/CUT_ATTEMPT/PASTE_ATTEMPT. `allowed` is the resolved per-assessment policy
    // for that specific action; occurrenceIndex counts only occurrences made *while disallowed*
    // (an allowed copy/paste is always INFO regardless of how many times it happens).
    public IntegritySeverity classifyClipboardAction(boolean allowed, int occurrenceIndexWhileDisallowed) {
        if (allowed) {
            return IntegritySeverity.INFO;
        }
        return occurrenceIndexWhileDisallowed > properties.getClipboardEscalateAfterCount()
                ? IntegritySeverity.HIGH
                : IntegritySeverity.MEDIUM;
    }

    public IntegritySeverity classifyFullscreenExit(boolean required, int occurrenceIndexWhileRequired) {
        if (!required) {
            return IntegritySeverity.INFO;
        }
        return occurrenceIndexWhileRequired > properties.getFullscreenEscalateAfterCount()
                ? IntegritySeverity.HIGH
                : IntegritySeverity.MEDIUM;
    }

    public IntegritySeverity classifyNavigationViolation() {
        return IntegritySeverity.LOW;
    }

    // Server-synthesized only -- see IntegrityEventService#heartbeat.
    public IntegritySeverity classifyMultipleSession() {
        return IntegritySeverity.HIGH;
    }

    // A second occurrence of the same physical action reported through a different browser API
    // (blur firing alongside visibilitychange, or vice versa) -- kept for audit completeness but
    // excluded from scoring as its own logical event (goal #4).
    public IntegritySeverity classifyCorrelatedDuplicate() {
        return IntegritySeverity.INFO;
    }

    private IntegritySeverity escalate(IntegritySeverity severity) {
        return switch (severity) {
            case INFO -> IntegritySeverity.LOW;
            case LOW -> IntegritySeverity.MEDIUM;
            case MEDIUM -> IntegritySeverity.HIGH;
            case HIGH, CRITICAL -> IntegritySeverity.CRITICAL;
        };
    }
}
