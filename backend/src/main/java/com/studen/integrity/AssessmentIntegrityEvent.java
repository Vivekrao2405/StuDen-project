package com.studen.integrity;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One behavioral signal reported for a {@link PracticalAttempt} (or server-synthesized, e.g.
 * {@code MULTIPLE_SESSION}). {@code clientEventId} is the idempotency key -- the unique
 * constraint below makes a duplicate delivery a safe no-op (see
 * {@code IntegrityEventService#recordBatch}). {@code severity} is always written server-side by
 * {@link IntegrityEventClassifier}; {@code metadata} only ever holds small, non-sensitive facts
 * (e.g. computed away-duration) -- never clipboard contents, keystrokes, or tokens.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_integrity_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"practical_attempt_id", "client_event_id"}))
public class AssessmentIntegrityEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_id", nullable = false)
    private PracticalAttempt practicalAttempt;

    @Column(name = "client_event_id", nullable = false)
    private UUID clientEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private IntegrityEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegritySeverity severity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "session_id")
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public AssessmentIntegrityEvent(PracticalAttempt practicalAttempt, UUID clientEventId, IntegrityEventType eventType,
            IntegritySeverity severity, Instant occurredAt, String sessionId, String metadata) {
        this.practicalAttempt = practicalAttempt;
        this.clientEventId = clientEventId;
        this.eventType = eventType;
        this.severity = severity;
        this.occurredAt = occurredAt;
        this.sessionId = sessionId;
        this.metadata = metadata;
    }
}
