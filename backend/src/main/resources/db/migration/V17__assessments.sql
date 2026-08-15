-- Phase 7.2: Assessment Engine. Reuses skills/users/questions/question_options via FK — no
-- duplicated content tables. assessment_type/status are string-enum columns so future assessment
-- types (CODING, SQL, DESIGN, ...) slot in without a schema change.

CREATE TABLE assessments (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users(id),
    skill_id          UUID NOT NULL REFERENCES skills(id),
    assessment_type   VARCHAR(20) NOT NULL DEFAULT 'KNOWLEDGE',
    status            VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    total_questions   INT NOT NULL,
    correct_count     INT,
    score_percentage  INT,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at      TIMESTAMPTZ,
    time_limit_seconds INT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_assessments_user_id ON assessments (user_id);
CREATE INDEX idx_assessments_skill_id ON assessments (skill_id);
CREATE INDEX idx_assessments_status ON assessments (status);
-- Hot path: "does this user already have an IN_PROGRESS assessment for this skill" on every Start.
CREATE INDEX idx_assessments_user_skill_status ON assessments (user_id, skill_id, status);

-- One row per question actually included in a generated assessment instance. question_id points
-- at the immutable PUBLISHED Question row selected at generation time; Phase 7.1's versioning
-- (editing a PUBLISHED question forks a new row) already guarantees this reference never mutates
-- or gets deleted out from under a historical assessment.
CREATE TABLE assessment_questions (
    id             UUID PRIMARY KEY,
    assessment_id  UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    question_id    UUID NOT NULL REFERENCES questions(id),
    display_order  INT NOT NULL DEFAULT 0,
    points         INT NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_assessment_questions_assessment_question UNIQUE (assessment_id, question_id)
);

CREATE INDEX idx_assessment_questions_assessment_id ON assessment_questions (assessment_id);

-- Per-instance randomized option order AND the authoritative set of option IDs that are legal to
-- submit for this assessment question — answer validation checks this table, never the live
-- Question Bank option list, so cross-question/unknown option IDs are a simple existence check.
CREATE TABLE assessment_question_options (
    id                      UUID PRIMARY KEY,
    assessment_question_id  UUID NOT NULL REFERENCES assessment_questions(id) ON DELETE CASCADE,
    question_option_id      UUID NOT NULL REFERENCES question_options(id),
    display_order           INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_aqo_question_option UNIQUE (assessment_question_id, question_option_id)
);

CREATE INDEX idx_assessment_question_options_aq_id ON assessment_question_options (assessment_question_id);

-- One row per answered question; selected_option_ids (below) holds 1..N option IDs depending on
-- question type. Upserted on every autosave call.
CREATE TABLE assessment_answers (
    id                      UUID PRIMARY KEY,
    assessment_id           UUID NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    assessment_question_id  UUID NOT NULL REFERENCES assessment_questions(id) ON DELETE CASCADE,
    answered_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_assessment_answers_question UNIQUE (assessment_question_id)
);

CREATE INDEX idx_assessment_answers_assessment_id ON assessment_answers (assessment_id);
CREATE INDEX idx_assessment_answers_assessment_question_id ON assessment_answers (assessment_question_id);

CREATE TABLE assessment_answer_selected_options (
    assessment_answer_id  UUID NOT NULL REFERENCES assessment_answers(id) ON DELETE CASCADE,
    option_id             UUID NOT NULL,
    PRIMARY KEY (assessment_answer_id, option_id)
);
