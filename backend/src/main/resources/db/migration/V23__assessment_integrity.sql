-- Phase 7.6: Assessment Integrity & Anti-Cheating System. Reuses practical_attempts /
-- execution_jobs as-is (V20/V21) -- no duplicated run/compile/submission storage. This is an
-- evidence-collection + deterministic-scoring layer on top of the existing attempt lifecycle,
-- entirely additive: no existing column changes meaning, no scoring-logic column touched.

-- One row per client-reported (or server-synthesized, e.g. MULTIPLE_SESSION) integrity signal.
-- severity is always server-computed (see com.studen.integrity.IntegrityEventClassifier) -- the
-- client only ever supplies event_type/occurred_at/session_id/metadata.
CREATE TABLE assessment_integrity_events (
    id                     UUID PRIMARY KEY,
    practical_attempt_id   UUID NOT NULL REFERENCES practical_attempts(id) ON DELETE CASCADE,
    client_event_id        UUID NOT NULL,
    event_type             VARCHAR(40) NOT NULL,
    severity               VARCHAR(20) NOT NULL,
    occurred_at             TIMESTAMPTZ NOT NULL,
    session_id              VARCHAR(100),
    -- Small, non-sensitive JSON only (e.g. {"awayDurationMs":7000}) -- never clipboard
    -- contents/keystrokes/tokens. See IntegrityEventService.
    metadata                TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_integrity_event_client_id UNIQUE (practical_attempt_id, client_event_id)
);

CREATE INDEX idx_integrity_events_attempt ON assessment_integrity_events (practical_attempt_id, occurred_at);

-- Lightweight heartbeat/session presence tracking for multiple-active-session detection.
-- Upserted in place (one row per attempt+session_id) -- never accumulates unbounded rows.
CREATE TABLE practical_attempt_sessions (
    id                     UUID PRIMARY KEY,
    practical_attempt_id   UUID NOT NULL REFERENCES practical_attempts(id) ON DELETE CASCADE,
    session_id              VARCHAR(100) NOT NULL,
    last_seen_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempt_session UNIQUE (practical_attempt_id, session_id)
);

-- Cached integrity summary, recomputed deterministically from assessment_integrity_events on
-- every write (com.studen.integrity.IntegrityScoringService.recompute) -- mirrors how
-- score/max_score already live directly on this entity. Entirely separate from those technical
-- scoring columns; nothing here is ever read by PracticalAttemptService's grading path.
-- Nullable at the DB level, but every PracticalAttempt insert always carries an explicit
-- 100/CLEAN from the entity's Java-side field default (see PracticalAttempt.java) -- a DB-level
-- DEFAULT would be redundant for JPA-created rows and this schema has no raw-SQL insert path.
ALTER TABLE practical_attempts
    ADD COLUMN integrity_score INT,
    ADD COLUMN integrity_status VARCHAR(20),
    ADD COLUMN tab_switch_count INT NOT NULL DEFAULT 0,
    ADD COLUMN copy_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN paste_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN cut_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN fullscreen_exit_count INT NOT NULL DEFAULT 0,
    ADD COLUMN multiple_session_count INT NOT NULL DEFAULT 0,
    ADD COLUMN total_integrity_event_count INT NOT NULL DEFAULT 0,
    ADD COLUMN suspicious_event_count INT NOT NULL DEFAULT 0,
    ADD COLUMN critical_event_count INT NOT NULL DEFAULT 0,
    -- Manual admin review override -- never auto-set. Effective status shown everywhere is
    -- integrity_override_status if present, else the computed integrity_status.
    ADD COLUMN integrity_override_status VARCHAR(20),
    ADD COLUMN integrity_override_reason TEXT,
    ADD COLUMN integrity_override_by UUID REFERENCES users(id),
    ADD COLUMN integrity_overridden_at TIMESTAMPTZ;
