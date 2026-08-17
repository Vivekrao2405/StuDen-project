-- Phase 7.5: Secure Code & SQL Execution Sandbox. Reuses practical_attempts/practical_assessments/
-- practical_test_cases as-is (V20) -- no duplicated content storage. execution_jobs is the
-- run-history/audit trail behind the Run/Submit buttons; execution_test_results is its per-test
-- breakdown. Status/kind columns are plain VARCHAR, same convention as every prior migration in
-- this schema, so new statuses slot in without a schema change.

-- One row per Run click or per final Submit. source_code is a snapshot at that moment,
-- independent of practical_attempts.submission_content (which keeps autosaving after a run) --
-- needed so run history reflects what was actually executed, not what's in the editor now.
CREATE TABLE execution_jobs (
    id                          UUID PRIMARY KEY,
    practical_attempt_id        UUID NOT NULL REFERENCES practical_attempts(id) ON DELETE CASCADE,
    kind                        VARCHAR(10) NOT NULL,
    language                    VARCHAR(10),
    source_code                 TEXT NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    tests_passed                INT,
    tests_total                 INT,
    -- Sanitized before storage: in-container paths only (always /workspace/...), never a host
    -- path/env var/secret -- see DockerCodeExecutionService.
    compile_error               TEXT,
    runtime_error               TEXT,
    duration_ms                 BIGINT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ
);

CREATE INDEX idx_execution_jobs_attempt_id ON execution_jobs (practical_attempt_id);
-- Run-history read: "every execution for this attempt, oldest first" (Run #1, #2, #3...).
CREATE INDEX idx_execution_jobs_attempt_created ON execution_jobs (practical_attempt_id, created_at);

-- Per-test-case outcome for one execution_job. hidden is denormalized from practical_test_cases at
-- run time -- a later admin edit toggling a test's hidden flag must never retroactively change what
-- a historical run is allowed to reveal to the student who ran it.
CREATE TABLE execution_test_results (
    id                     UUID PRIMARY KEY,
    execution_job_id       UUID NOT NULL REFERENCES execution_jobs(id) ON DELETE CASCADE,
    test_case_id           UUID NOT NULL REFERENCES practical_test_cases(id),
    hidden                 BOOLEAN NOT NULL,
    passed                 BOOLEAN NOT NULL,
    -- Only ever serialized to the student when hidden = false -- see ExecutionTestResultResponse.
    actual_output          TEXT,
    execution_time_ms      BIGINT,
    status                 VARCHAR(20) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_execution_test_results_job_id ON execution_test_results (execution_job_id);

-- Set once, the first time any execution_job for this attempt reaches a post-compile status --
-- feeds Phase 7.6's Assessment Integrity system later; nothing in this phase reads it back.
ALTER TABLE practical_attempts ADD COLUMN first_successful_compilation_at TIMESTAMPTZ;

-- Per-question configurable output comparison (spec: "do NOT blindly trim everything"). Additive,
-- defaults every existing row to the previous de facto behavior of this codebase's manual-review
-- flow (a human eyeballing output tolerates trailing-newline differences).
ALTER TABLE practical_test_cases ADD COLUMN comparison_mode VARCHAR(20) NOT NULL DEFAULT 'NORMALIZE_NEWLINES';
