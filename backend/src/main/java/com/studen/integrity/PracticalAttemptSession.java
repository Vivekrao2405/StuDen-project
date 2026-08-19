package com.studen.integrity;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Heartbeat presence per browser tab/session for one {@link PracticalAttempt} -- upserted in
 * place (one row per attempt+sessionId, never accumulates), used only to detect multiple
 * concurrently-active sessions (see {@code IntegrityEventService#heartbeat}). Not a full session/
 * auth mechanism -- purely an integrity signal.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_attempt_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"practical_attempt_id", "session_id"}))
public class PracticalAttemptSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_id", nullable = false)
    private PracticalAttempt practicalAttempt;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public PracticalAttemptSession(PracticalAttempt practicalAttempt, String sessionId, Instant lastSeenAt) {
        this.practicalAttempt = practicalAttempt;
        this.sessionId = sessionId;
        this.lastSeenAt = lastSeenAt;
    }
}
