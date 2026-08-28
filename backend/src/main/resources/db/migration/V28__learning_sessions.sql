-- Personalized Learning Roadmap + Calendar: a scheduled study session is student-created (never
-- auto-filled) and normally references a real, currently-published resource. `resource_id` is
-- nullable specifically for a generated study plan's resource-less "Practice / Revision" slot
-- (section 14's Friday example) — every other session always carries a real resource. `topic`
-- records which weak topic the session was scheduled against, since one resource can match more
-- than one topic (see TagParser) and the roadmap/calendar need to stay linked to the specific
-- roadmap item, not just the resource.

CREATE TABLE learning_sessions (
    id                UUID PRIMARY KEY,
    student_id        UUID NOT NULL REFERENCES users(id),
    resource_id       UUID REFERENCES resources(id) ON DELETE CASCADE,
    topic             VARCHAR(100),
    scheduled_start   TIMESTAMPTZ NOT NULL,
    duration_minutes  INT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Calendar range queries (GET /calendar/sessions?from=&to=) are always scoped to one student.
CREATE INDEX idx_learning_sessions_student_start ON learning_sessions (student_id, scheduled_start);

-- Completion-sync lookup (ResourceSessionSyncListener finding SCHEDULED sessions for a resource
-- just marked COMPLETED via the resource detail page rather than via the calendar).
CREATE INDEX idx_learning_sessions_student_resource_status ON learning_sessions (student_id, resource_id, status);
