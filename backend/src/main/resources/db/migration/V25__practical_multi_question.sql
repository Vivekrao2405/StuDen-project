-- Phase 7.6: Multi-Question Practical Assessments + Automated Evaluation. Splits what used to be
-- a 1:1 "one practical_assessment = one problem" model (V20) into a real
-- assessment -> questions -> (test cases/languages/rubric) hierarchy, mirroring the
-- assessment/assessment_questions split already used by the MCQ engine (V17). Every existing row
-- is migrated forward with zero data loss: each pre-existing practical_assessments row gets
-- exactly one practical_questions row (its former content), and each pre-existing
-- practical_attempts row gets exactly one practical_attempt_questions row (its former
-- submission/score) -- so every single-question assessment/attempt made before this migration
-- keeps working, unchanged, as a "1-question assessment" going forward.

-- ============================================================================
-- 1. practical_questions -- the new home for per-question problem content. practicalType/
--    workspaceType/evaluationType/timeLimitMinutes stay on practical_assessments (uniform across
--    every question in one assessment) -- only content, points, and an optional skill/difficulty
--    override vary per question.
-- ============================================================================
CREATE TABLE practical_questions (
    id                       UUID PRIMARY KEY,
    practical_assessment_id  UUID NOT NULL REFERENCES practical_assessments(id) ON DELETE CASCADE,
    title                    VARCHAR(255) NOT NULL,
    skill_id                 UUID REFERENCES skills(id),
    difficulty               VARCHAR(10),
    instructions             TEXT NOT NULL,
    requirements             TEXT,
    constraints              TEXT,
    -- Question-level free-form settings (e.g. sqlOrderedComparison) -- distinct from
    -- practical_assessments.configuration_json, which stays assessment-wide (integrityPolicy).
    configuration_json       TEXT,
    points                   INT NOT NULL DEFAULT 100,
    display_order            INT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practical_questions_assessment_id ON practical_questions (practical_assessment_id, display_order);

-- One row per legacy practical_assessments row -- carries its former instructions/requirements/
-- constraints/configuration_json forward unchanged, as question #1 of a now-1-question assessment.
INSERT INTO practical_questions (id, practical_assessment_id, title, instructions, requirements, constraints,
        configuration_json, points, display_order, created_at, updated_at)
SELECT gen_random_uuid(), id, title, instructions, requirements, constraints, configuration_json, 100, 0,
        created_at, updated_at
FROM practical_assessments;

ALTER TABLE practical_assessments DROP COLUMN requirements;
ALTER TABLE practical_assessments DROP COLUMN constraints;

-- ============================================================================
-- 2. Repoint practical_coding_languages / practical_test_cases / practical_rubric_criteria from
--    practical_assessment_id to practical_question_id.
-- ============================================================================
ALTER TABLE practical_coding_languages ADD COLUMN practical_question_id UUID REFERENCES practical_questions(id) ON DELETE CASCADE;
UPDATE practical_coding_languages pcl SET practical_question_id = pq.id
    FROM practical_questions pq WHERE pq.practical_assessment_id = pcl.practical_assessment_id;
ALTER TABLE practical_coding_languages ALTER COLUMN practical_question_id SET NOT NULL;
ALTER TABLE practical_coding_languages DROP CONSTRAINT uq_practical_coding_languages_assessment_language;
DROP INDEX idx_practical_coding_languages_assessment_id;
ALTER TABLE practical_coding_languages DROP COLUMN practical_assessment_id;
ALTER TABLE practical_coding_languages ADD CONSTRAINT uq_practical_coding_languages_question_language UNIQUE (practical_question_id, language);
CREATE INDEX idx_practical_coding_languages_question_id ON practical_coding_languages (practical_question_id);

ALTER TABLE practical_test_cases ADD COLUMN practical_question_id UUID REFERENCES practical_questions(id) ON DELETE CASCADE;
UPDATE practical_test_cases ptc SET practical_question_id = pq.id
    FROM practical_questions pq WHERE pq.practical_assessment_id = ptc.practical_assessment_id;
ALTER TABLE practical_test_cases ALTER COLUMN practical_question_id SET NOT NULL;
DROP INDEX idx_practical_test_cases_assessment_id;
ALTER TABLE practical_test_cases DROP COLUMN practical_assessment_id;
CREATE INDEX idx_practical_test_cases_question_id ON practical_test_cases (practical_question_id);

ALTER TABLE practical_rubric_criteria ADD COLUMN practical_question_id UUID REFERENCES practical_questions(id) ON DELETE CASCADE;
UPDATE practical_rubric_criteria prc SET practical_question_id = pq.id
    FROM practical_questions pq WHERE pq.practical_assessment_id = prc.practical_assessment_id;
ALTER TABLE practical_rubric_criteria ALTER COLUMN practical_question_id SET NOT NULL;
DROP INDEX idx_practical_rubric_criteria_assessment_id;
ALTER TABLE practical_rubric_criteria DROP COLUMN practical_assessment_id;
CREATE INDEX idx_practical_rubric_criteria_question_id ON practical_rubric_criteria (practical_question_id);

-- ============================================================================
-- 3. practical_attempt_questions -- per-question submission/state/score for one attempt. Mirrors
--    what practical_attempts held directly pre-migration (submission_content/selected_language/
--    submission_file_url/submission_link_url/first_successful_compilation_at/score/max_score) --
--    those columns stay on practical_attempts too (nothing dropped there, avoids any risk to
--    historical data) but are no longer written by new code; practical_attempts.score/max_score
--    become the attempt-wide TOTAL (sum across questions) going forward.
-- ============================================================================
CREATE TABLE practical_attempt_questions (
    id                              UUID PRIMARY KEY,
    practical_attempt_id            UUID NOT NULL REFERENCES practical_attempts(id) ON DELETE CASCADE,
    practical_question_id           UUID NOT NULL REFERENCES practical_questions(id),
    display_order                   INT NOT NULL DEFAULT 0,
    points_possible                 INT NOT NULL DEFAULT 0,
    points_earned                   INT,
    tests_passed                    INT,
    tests_total                     INT,
    status                          VARCHAR(20) NOT NULL DEFAULT 'NOT_ATTEMPTED',
    submission_content              TEXT,
    selected_language               VARCHAR(10),
    submission_file_url             VARCHAR(1000),
    submission_link_url             VARCHAR(1000),
    first_successful_compilation_at TIMESTAMPTZ,
    feedback                        TEXT,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempt_question UNIQUE (practical_attempt_id, practical_question_id)
);

CREATE INDEX idx_practical_attempt_questions_attempt_id ON practical_attempt_questions (practical_attempt_id, display_order);
CREATE INDEX idx_practical_attempt_questions_question_id ON practical_attempt_questions (practical_question_id);

-- Backfill: one attempt_question per legacy attempt, pointing at that assessment's (now sole)
-- question, carrying its former submission/score forward. Status is a best-effort historical
-- reconstruction -- this data only ever drives display of old attempts, never re-graded.
INSERT INTO practical_attempt_questions (id, practical_attempt_id, practical_question_id, display_order,
        points_possible, points_earned, status, submission_content, selected_language, submission_file_url,
        submission_link_url, first_successful_compilation_at, created_at, updated_at)
SELECT gen_random_uuid(), pa.id, pq.id, 0,
        COALESCE(pa.max_score, 100), pa.score,
        CASE
            WHEN pa.status = 'IN_PROGRESS' AND pa.submission_content IS NOT NULL THEN 'IN_PROGRESS'
            WHEN pa.submission_content IS NULL AND pa.submission_link_url IS NULL AND pa.submission_file_url IS NULL THEN 'NOT_ATTEMPTED'
            WHEN pa.score IS NULL THEN 'UNDER_REVIEW'
            WHEN pa.score >= COALESCE(pa.max_score, 100) THEN 'PASSED'
            WHEN pa.score <= 0 THEN 'FAILED'
            ELSE 'PARTIAL'
        END,
        pa.submission_content, pa.selected_language, pa.submission_file_url, pa.submission_link_url,
        pa.first_successful_compilation_at, pa.created_at, pa.updated_at
FROM practical_attempts pa
JOIN practical_questions pq ON pq.practical_assessment_id = pa.practical_assessment_id;

-- ============================================================================
-- 4. execution_jobs -- add the per-question link. A job with test results could technically derive
--    its question via execution_test_results -> test_case_id -> practical_question_id, but a
--    COMPILATION_ERROR/SYSTEM_ERROR job has zero test results, so an explicit FK is required.
-- ============================================================================
ALTER TABLE execution_jobs ADD COLUMN practical_attempt_question_id UUID REFERENCES practical_attempt_questions(id) ON DELETE CASCADE;
UPDATE execution_jobs ej SET practical_attempt_question_id = paq.id
    FROM practical_attempt_questions paq WHERE paq.practical_attempt_id = ej.practical_attempt_id;
CREATE INDEX idx_execution_jobs_attempt_question_id ON execution_jobs (practical_attempt_question_id, created_at);
