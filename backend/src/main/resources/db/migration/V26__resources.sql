-- Phase 7.7: My Learning — admin-curated resources personalized to a student's weak
-- skills/tags. `resources` mirrors questions/practical_questions' shape (skill + free-form tags,
-- Cloudinary-backed file storage for uploaded types); `student_resource_progress` is the
-- per-student completion tracker, one row per (student, resource) once started.

CREATE TABLE resources (
    id                UUID PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    resource_type     VARCHAR(20) NOT NULL,
    skill_id          UUID NOT NULL REFERENCES skills(id),
    difficulty        VARCHAR(10),
    estimated_minutes INT,
    file_url          TEXT,
    file_public_id    VARCHAR(255),
    external_url      TEXT,
    notes_content     TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by        UUID NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Student-facing hot path: published resources for a set of weak skills.
CREATE INDEX idx_resources_skill_status ON resources (skill_id, status);
CREATE INDEX idx_resources_status ON resources (status);

CREATE TABLE resource_tags (
    resource_id UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    tag         VARCHAR(100) NOT NULL
);
CREATE INDEX idx_resource_tags_resource_id ON resource_tags (resource_id);
CREATE INDEX idx_resource_tags_tag ON resource_tags (tag);

CREATE TABLE student_resource_progress (
    id           UUID PRIMARY KEY,
    student_id   UUID NOT NULL REFERENCES users(id),
    resource_id  UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL,
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_resource UNIQUE (student_id, resource_id)
);
CREATE INDEX idx_student_resource_progress_student ON student_resource_progress (student_id);
