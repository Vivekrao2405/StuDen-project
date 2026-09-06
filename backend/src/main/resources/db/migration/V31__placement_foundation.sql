-- Placement System Phase 0 — the relational foundation for
-- Role -> Required Skills -> Assessment -> Skill Scores -> Skill Gaps -> Learning Resources ->
-- Placement Prep. Nothing here computes readiness or recommendations; those belong to later
-- phases and must read this schema rather than hard-coding role/skill knowledge anywhere.
--
-- Deliberately reuses, and does NOT duplicate, existing infrastructure:
--   * users                 -- the only identity/auth table (placement_profiles.user_id)
--   * skills                -- the universal skill catalog from V3/V4 IS the placement Skill
--   * questions             -- the MCQ Question Bank from V16 stores placement MCQ/aptitude/
--                              technical/case-study questions (see V32 for why no new type enum)
--   * practical_assessments -- V20/V25 coding/practical infrastructure stores placement coding
--                              questions; the execution sandbox is untouched by this migration
--   * resources             -- V26 learning resources already carry skill_id, so the required
--                              "Skill -> Learning Resources" relation needs no new table at all
--
-- FK policy: an owned child row cascades from its parent (role -> role_skills,
-- series -> modules -> items, profile -> its collections). Everything pointing *sideways* at
-- shared catalog data (skills, companies, questions, practical assessments, resources, roles)
-- restricts, so deleting catalog data can never silently shred placement content or student
-- progress. Where a cascade would reach student rows (series/module/item deletion), the service
-- layer refuses the delete while progress exists -- the posture AdminResourceService already takes.

-- ---------------------------------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_roles (
    id              UUID PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_placement_roles_name ON placement_roles (name);
CREATE UNIQUE INDEX uk_placement_roles_normalized_name ON placement_roles (normalized_name);
CREATE INDEX idx_placement_roles_status ON placement_roles (status);

-- ---------------------------------------------------------------------------------------------
-- Companies
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_companies (
    id              UUID PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    company_type    VARCHAR(30) NOT NULL,
    description     TEXT,
    logo_url        TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_placement_companies_name ON placement_companies (name);
CREATE UNIQUE INDEX uk_placement_companies_normalized_name ON placement_companies (normalized_name);
CREATE INDEX idx_placement_companies_type_status ON placement_companies (company_type, status);

-- ---------------------------------------------------------------------------------------------
-- Role -> Skill mapping. A real relation, never a JSON blob: it has to answer
-- "what skills does this role require, and how much does each one matter?"
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_role_skills (
    id                   UUID PRIMARY KEY,
    role_id              UUID NOT NULL REFERENCES placement_roles(id) ON DELETE CASCADE,
    skill_id             UUID NOT NULL REFERENCES skills(id),
    weight               INT NOT NULL DEFAULT 1,
    -- Nullable = no explicit target set. Values come from the same 5-tier scale a knowledge
    -- assessment already produces (com.studen.assessment.AssessmentLevel), so Phase 3 can compare
    -- a measured level against the required level directly instead of inventing a second scale.
    required_proficiency VARCHAR(20),
    priority             INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_placement_role_skill UNIQUE (role_id, skill_id)
);
CREATE INDEX idx_placement_role_skills_role ON placement_role_skills (role_id, priority);
CREATE INDEX idx_placement_role_skills_skill ON placement_role_skills (skill_id);

-- ---------------------------------------------------------------------------------------------
-- Student placement profile (exactly one per user)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_profiles (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id),
    target_role_id   UUID NOT NULL REFERENCES placement_roles(id),
    experience_level VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_placement_profile_user UNIQUE (user_id)
);
CREATE INDEX idx_placement_profiles_role ON placement_profiles (target_role_id);

-- companyTypes[] -- a set of enum values, not a comma-separated string.
CREATE TABLE placement_profile_company_types (
    profile_id   UUID NOT NULL REFERENCES placement_profiles(id) ON DELETE CASCADE,
    company_type VARCHAR(30) NOT NULL,
    PRIMARY KEY (profile_id, company_type)
);

-- targetCompanies[]
CREATE TABLE placement_profile_companies (
    profile_id UUID NOT NULL REFERENCES placement_profiles(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES placement_companies(id),
    PRIMARY KEY (profile_id, company_id)
);
CREATE INDEX idx_placement_profile_companies_company ON placement_profile_companies (company_id);

-- currentSkills[] -- points at the shared skills catalog, exactly as portfolio_skills does.
CREATE TABLE placement_profile_skills (
    profile_id UUID NOT NULL REFERENCES placement_profiles(id) ON DELETE CASCADE,
    skill_id   UUID NOT NULL REFERENCES skills(id),
    PRIMARY KEY (profile_id, skill_id)
);
CREATE INDEX idx_placement_profile_skills_skill ON placement_profile_skills (skill_id);

-- ---------------------------------------------------------------------------------------------
-- Role-specific readiness assessment definition (admin-configured). The attempted side lives in
-- placement_attempts below: deliberately the template/attempt split practical_assessments +
-- practical_attempts already uses, NOT the com.studen.assessment shape (that table is one row per
-- student per generated skill quiz and has no admin-authored template to configure).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_assessments (
    id               UUID PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    role_id          UUID NOT NULL REFERENCES placement_roles(id),
    difficulty       VARCHAR(10) NOT NULL,
    duration_minutes INT,
    passing_score    INT,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by       UUID NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_placement_assessments_role_status ON placement_assessments (role_id, status);

CREATE TABLE placement_assessment_questions (
    id                      UUID PRIMARY KEY,
    placement_assessment_id UUID NOT NULL REFERENCES placement_assessments(id) ON DELETE CASCADE,
    question_id             UUID NOT NULL REFERENCES questions(id),
    display_order           INT NOT NULL DEFAULT 0,
    points                  INT NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_placement_assessment_question UNIQUE (placement_assessment_id, question_id)
);
CREATE INDEX idx_placement_assessment_questions_question ON placement_assessment_questions (question_id);

-- ---------------------------------------------------------------------------------------------
-- Attempts + skill scores. Foundation only: Phase 0 adds no scoring logic and no endpoints here.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_attempts (
    id                      UUID PRIMARY KEY,
    student_id              UUID NOT NULL REFERENCES users(id),
    placement_assessment_id UUID NOT NULL REFERENCES placement_assessments(id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at              TIMESTAMPTZ NOT NULL,
    submitted_at            TIMESTAMPTZ,
    score                   INT,
    max_score               INT,
    score_percentage        INT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_placement_attempts_student ON placement_attempts (student_id, started_at DESC);
CREATE INDEX idx_placement_attempts_assessment ON placement_attempts (placement_assessment_id);

-- A history, not a single current value: readiness is measured repeatedly (Assess -> ... ->
-- Reassess), so every measurement is kept along with the attempt that produced it. attempt_id is
-- nullable + ON DELETE SET NULL so a recorded score survives even if its source attempt is purged.
CREATE TABLE placement_skill_scores (
    id               UUID PRIMARY KEY,
    student_id       UUID NOT NULL REFERENCES users(id),
    skill_id         UUID NOT NULL REFERENCES skills(id),
    attempt_id       UUID REFERENCES placement_attempts(id) ON DELETE SET NULL,
    score_percentage INT NOT NULL,
    correct_count    INT,
    total_questions  INT,
    recorded_at      TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_placement_skill_scores_student_skill
    ON placement_skill_scores (student_id, skill_id, recorded_at DESC);
CREATE INDEX idx_placement_skill_scores_attempt ON placement_skill_scores (attempt_id);

-- ---------------------------------------------------------------------------------------------
-- Placement Prep: series -> modules -> items
-- ---------------------------------------------------------------------------------------------
CREATE TABLE placement_series (
    id                       UUID PRIMARY KEY,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,
    company_id               UUID REFERENCES placement_companies(id),
    target_role_id           UUID NOT NULL REFERENCES placement_roles(id),
    company_type             VARCHAR(30),
    difficulty               VARCHAR(10) NOT NULL,
    estimated_duration_hours INT,
    thumbnail_url            TEXT,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by               UUID NOT NULL REFERENCES users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_placement_series_role_status ON placement_series (target_role_id, status);
CREATE INDEX idx_placement_series_company ON placement_series (company_id);

CREATE TABLE placement_series_skills (
    series_id UUID NOT NULL REFERENCES placement_series(id) ON DELETE CASCADE,
    skill_id  UUID NOT NULL REFERENCES skills(id),
    PRIMARY KEY (series_id, skill_id)
);
CREATE INDEX idx_placement_series_skills_skill ON placement_series_skills (skill_id);

-- display_order is intentionally NOT unique per series: reordering swaps values in place and a
-- unique constraint would reject the intermediate state (same reason topics.display_order has none).
CREATE TABLE placement_modules (
    id                  UUID PRIMARY KEY,
    series_id           UUID NOT NULL REFERENCES placement_series(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    display_order       INT NOT NULL DEFAULT 0,
    -- NULL = every required item must be completed. A number = complete at least N items.
    required_item_count INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_placement_modules_series ON placement_modules (series_id, display_order);

-- One row = one piece of content inside a module. item_type says which of the three nullable FKs
-- is populated; exactly one always is, enforced by the CHECK below and by the service layer.
-- Three typed FK columns rather than a polymorphic (type, uuid) pair so the database itself
-- guarantees the referenced content actually exists.
CREATE TABLE placement_module_items (
    id                      UUID PRIMARY KEY,
    module_id               UUID NOT NULL REFERENCES placement_modules(id) ON DELETE CASCADE,
    item_type               VARCHAR(30) NOT NULL,
    question_id             UUID REFERENCES questions(id),
    practical_assessment_id UUID REFERENCES practical_assessments(id),
    resource_id             UUID REFERENCES resources(id),
    display_order           INT NOT NULL DEFAULT 0,
    required                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_placement_module_item_target CHECK (
        (item_type = 'QUESTION'
            AND question_id IS NOT NULL AND practical_assessment_id IS NULL AND resource_id IS NULL)
     OR (item_type = 'PRACTICAL_ASSESSMENT'
            AND question_id IS NULL AND practical_assessment_id IS NOT NULL AND resource_id IS NULL)
     OR (item_type = 'RESOURCE'
            AND question_id IS NULL AND practical_assessment_id IS NULL AND resource_id IS NOT NULL)
    )
);
CREATE INDEX idx_placement_module_items_module ON placement_module_items (module_id, display_order);
-- Partial uniques: the same content cannot be added to one module twice, while the two unused
-- NULL columns on every row must not collide -- which a plain UNIQUE constraint would allow.
CREATE UNIQUE INDEX uk_placement_module_item_question
    ON placement_module_items (module_id, question_id) WHERE question_id IS NOT NULL;
CREATE UNIQUE INDEX uk_placement_module_item_practical
    ON placement_module_items (module_id, practical_assessment_id) WHERE practical_assessment_id IS NOT NULL;
CREATE UNIQUE INDEX uk_placement_module_item_resource
    ON placement_module_items (module_id, resource_id) WHERE resource_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Student progress through placement content. Split in two (series roll-up + per-item detail)
-- rather than one table with nullable module/item columns, because Postgres does not deduplicate
-- NULLs inside a UNIQUE constraint -- a single table keyed on (student, series, NULL, NULL) could
-- silently accumulate duplicate series rows. Module-level progress derives from the item rows.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE student_series_progress (
    id           UUID PRIMARY KEY,
    student_id   UUID NOT NULL REFERENCES users(id),
    series_id    UUID NOT NULL REFERENCES placement_series(id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_series_progress UNIQUE (student_id, series_id)
);
CREATE INDEX idx_student_series_progress_student ON student_series_progress (student_id);

CREATE TABLE student_module_item_progress (
    id             UUID PRIMARY KEY,
    student_id     UUID NOT NULL REFERENCES users(id),
    module_item_id UUID NOT NULL REFERENCES placement_module_items(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_module_item_progress UNIQUE (student_id, module_item_id)
);
CREATE INDEX idx_student_module_item_progress_student ON student_module_item_progress (student_id);
CREATE INDEX idx_student_module_item_progress_item ON student_module_item_progress (module_item_id);
