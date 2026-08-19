package com.studen.integrity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

// One client-reported signal. `severity` is deliberately absent -- the server is the only one
// that ever assigns it (see IntegrityEventClassifier). `occurredAt` is the client's own clock;
// the server clamps it into a sane range rather than trusting it outright.
public record IntegrityEventRequest(
        @NotNull UUID clientEventId,
        @NotNull IntegrityEventType eventType,
        @NotNull Instant occurredAt,
        @Size(max = 100) String sessionId,
        @Size(max = 500) String metadata) {
}
