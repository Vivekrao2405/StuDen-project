-- Phase 7.4: Practical Assessment Engine. Structurally separate from the Question Bank/Knowledge
-- Assessment Engine (V16/V17) — a practical task has no MCQ options, needs a code/design/file
-- submission, and is usually manually evaluated. Reuses skills/users via FK, no duplicated
-- content tables. Status/type columns are plain VARCHAR (not native Postgres enums), same
-- convention as V16/V17, so new enum values slot in without a schema change.

CREATE TABLE practical_assessments (
    id                   UUID PRIMARY KEY,
    title                VARCHAR(255) NOT NULL,
    skill_id             UUID NOT NULL REFERENCES skills(id),
    practical_type       VARCHAR(30) NOT NULL,
    workspace_type       VARCHAR(30) NOT NULL,
    difficulty           VARCHAR(10) NOT NULL,
    time_limit_minutes   INT NOT NULL,
    instructions         TEXT NOT NULL,
    requirements         TEXT,
    constraints          TEXT,
    evaluation_type      VARCHAR(10) NOT NULL,
    status               VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    version              INT NOT NULL DEFAULT 1,
    previous_version_id  UUID REFERENCES practical_assessments(id),
    created_by           UUID NOT NULL REFERENCES users(id),
    reviewed_by          UUID REFERENCES users(id),
    -- Raw JSON text (not a jsonb column — no jsonb precedent elsewhere in this schema, and this
    -- value is always read/written wholesale by the owning workspace, never queried by field).
    -- Per-workspaceType free-form settings: SQL schema description, web starter template, UI/UX
    -- submission mode + reference image, generic dataset link.
    configuration_json   TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practical_assessments_skill_id ON practical_assessments (skill_id);
CREATE INDEX idx_practical_assessments_practical_type ON practical_assessments (practical_type);
CREATE INDEX idx_practical_assessments_difficulty ON practical_assessments (difficulty);
CREATE INDEX idx_practical_assessments_status ON practical_assessments (status);

-- One supported language + starter code per CODING assessment. The problem statement/constraints/
-- test cases live once on practical_assessments and are shared by every language row here — this
-- is what keeps "one coding problem = one problem definition" true (spec §5/§36).
CREATE TABLE practical_coding_languages (
    id                       UUID PRIMARY KEY,
    practical_assessment_id  UUID NOT NULL REFERENCES practical_assessments(id) ON DELETE CASCADE,
    language                 VARCHAR(10) NOT NULL,
    starter_code             TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_practical_coding_languages_assessment_language UNIQUE (practical_assessment_id, language)
);

CREATE INDEX idx_practical_coding_languages_assessment_id ON practical_coding_languages (practical_assessment_id);

-- Language-neutral input/expected-output pairs (spec §7/§8). `hidden` rows are the actual
-- evaluation cases and are filtered out at the DTO-mapping layer for every student-facing
-- response — never serialized to a student under any circumstance.
CREATE TABLE practical_test_cases (
    id                       UUID PRIMARY KEY,
    practical_assessment_id  UUID NOT NULL REFERENCES practical_assessments(id) ON DELETE CASCADE,
    input                    TEXT NOT NULL,
    expected_output          TEXT NOT NULL,
    hidden                   BOOLEAN NOT NULL DEFAULT false,
    display_order            INT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practical_test_cases_assessment_id ON practical_test_cases (practical_assessment_id);

-- Scored criteria for MANUAL/HYBRID-evaluated assessments (UI/UX, web, etc). Admin-declared
-- max_points must sum to 100 at publish time (enforced in AdminPracticalAssessmentService); the
-- awarded score always comes from practical_rubric_scores, never a client-supplied total.
CREATE TABLE practical_rubric_criteria (
    id                       UUID PRIMARY KEY,
    practical_assessment_id  UUID NOT NULL REFERENCES practical_assessments(id) ON DELETE CASCADE,
    criterion                VARCHAR(255) NOT NULL,
    max_points               INT NOT NULL,
    display_order            INT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practical_rubric_criteria_assessment_id ON practical_rubric_criteria (practical_assessment_id);

-- One student's attempt at a practical_assessment. practical_assessment_id pins to the exact
-- version attempted — never repointed by a later edit/new-version, same historical-integrity
-- guarantee as assessment_questions.question_id (V17). deadline is computed once at start
-- (started_at + assessment.time_limit_minutes) and is the sole source of truth for expiry.
CREATE TABLE practical_attempts (
    id                       UUID PRIMARY KEY,
    practical_assessment_id  UUID NOT NULL REFERENCES practical_assessments(id),
    user_id                  UUID NOT NULL REFERENCES users(id),
    status                   VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline                 TIMESTAMPTZ NOT NULL,
    submitted_at             TIMESTAMPTZ,
    evaluated_at             TIMESTAMPTZ,
    score                    INT,
    max_score                INT,
    feedback                 TEXT,
    selected_language        VARCHAR(10),
    submission_content       TEXT,
    submission_file_url      VARCHAR(1000),
    submission_link_url      VARCHAR(1000),
    evaluated_by             UUID REFERENCES users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practical_attempts_user_id ON practical_attempts (user_id);
CREATE INDEX idx_practical_attempts_assessment_id ON practical_attempts (practical_assessment_id);
CREATE INDEX idx_practical_attempts_status ON practical_attempts (status);
-- Hot path: "does this user already have an IN_PROGRESS attempt for this assessment" on every
-- Start/resume click — mirrors idx_assessments_user_skill_status (V17).
CREATE INDEX idx_practical_attempts_user_assessment_status ON practical_attempts (user_id, practical_assessment_id, status);

-- Admin-awarded points per rubric criterion per attempt. practical_attempts.score is always
-- sum(points_awarded) across these rows for that attempt, computed server-side.
CREATE TABLE practical_rubric_scores (
    id                     UUID PRIMARY KEY,
    practical_attempt_id   UUID NOT NULL REFERENCES practical_attempts(id) ON DELETE CASCADE,
    rubric_criterion_id    UUID NOT NULL REFERENCES practical_rubric_criteria(id),
    points_awarded         INT NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practical_rubric_scores_attempt_id ON practical_rubric_scores (practical_attempt_id);
