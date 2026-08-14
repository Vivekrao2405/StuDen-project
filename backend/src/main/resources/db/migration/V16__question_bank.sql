-- Phase 7.1: Question Bank foundation. Reuses the existing skills table (no duplicate skill/
-- category system) via skill_id FKs on both topics and questions.

CREATE TABLE topics (
    id               UUID PRIMARY KEY,
    skill_id         UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    normalized_name  VARCHAR(255) NOT NULL,
    display_order    INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_topics_skill_normalized_name UNIQUE (skill_id, normalized_name)
);

CREATE INDEX idx_topics_skill_id ON topics (skill_id);

CREATE TABLE questions (
    id                         UUID PRIMARY KEY,
    skill_id                   UUID NOT NULL REFERENCES skills(id),
    topic_id                   UUID REFERENCES topics(id),
    question_text              TEXT NOT NULL,
    normalized_question_text   TEXT NOT NULL,
    question_type              VARCHAR(20) NOT NULL,
    difficulty                 VARCHAR(10) NOT NULL,
    explanation                TEXT,
    status                     VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    estimated_time_seconds     INT,
    created_by                 UUID NOT NULL REFERENCES users(id),
    reviewed_by                UUID REFERENCES users(id),
    version                    INT NOT NULL DEFAULT 1,
    previous_version_id        UUID REFERENCES questions(id),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Individually indexed rather than assuming one composite covers every filter combination — the
-- admin list and the learner API filter on different subsets of these columns (see
-- QuestionRepository).
CREATE INDEX idx_questions_skill_id ON questions (skill_id);
CREATE INDEX idx_questions_topic_id ON questions (topic_id);
CREATE INDEX idx_questions_difficulty ON questions (difficulty);
CREATE INDEX idx_questions_status ON questions (status);
CREATE INDEX idx_questions_question_type ON questions (question_type);
-- The learner API's hot path: published questions for one skill.
CREATE INDEX idx_questions_skill_status ON questions (skill_id, status);
-- Non-unique on purpose — duplicate questions are a warning, never a hard block (spec allows
-- legitimate near-duplicates).
CREATE INDEX idx_questions_skill_normalized_text ON questions (skill_id, normalized_question_text);

CREATE TABLE question_options (
    id             UUID PRIMARY KEY,
    question_id    UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_text    TEXT NOT NULL,
    display_order  INT NOT NULL DEFAULT 0,
    is_correct     BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_options_question_id ON question_options (question_id);

CREATE TABLE question_tags (
    question_id  UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    tag          VARCHAR(100) NOT NULL,
    PRIMARY KEY (question_id, tag)
);
