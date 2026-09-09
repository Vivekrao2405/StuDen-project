-- Placement System Phase 3 — the readiness assessment engine (start/answer/submit/score).
--
-- Snapshots each attempt's exact question set (skill id + name) at start time, mirroring
-- assessment_questions' topic/tag snapshot (V18): a Question row is immutable once PUBLISHED, but
-- Skill.name is not version-locked, and the admin-editable placement_assessment_questions link
-- table can change after a student has already attempted it. Without this snapshot, either of
-- those later edits would silently rewrite a historical attempt's skill breakdown.

CREATE TABLE placement_attempt_questions (
    id                    UUID PRIMARY KEY,
    placement_attempt_id  UUID NOT NULL REFERENCES placement_attempts(id) ON DELETE CASCADE,
    question_id           UUID NOT NULL REFERENCES questions(id),
    skill_id              UUID NOT NULL REFERENCES skills(id),
    skill_name            VARCHAR(150) NOT NULL,
    display_order         INT NOT NULL DEFAULT 0,
    points                INT NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_placement_attempt_questions_attempt ON placement_attempt_questions (placement_attempt_id, display_order);
CREATE INDEX idx_placement_attempt_questions_skill ON placement_attempt_questions (skill_id);

-- One row per question, upserted on every save — mirrors assessment_answers exactly.
-- placement_attempt_question_id alone is unique because a PlacementAttemptQuestion already belongs
-- to exactly one attempt (it is per-attempt, not shared like placement_assessment_questions).
CREATE TABLE placement_attempt_answers (
    id                              UUID PRIMARY KEY,
    placement_attempt_id           UUID NOT NULL REFERENCES placement_attempts(id) ON DELETE CASCADE,
    placement_attempt_question_id  UUID NOT NULL REFERENCES placement_attempt_questions(id),
    answered_at                     TIMESTAMPTZ NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_placement_attempt_answer_question UNIQUE (placement_attempt_question_id)
);
CREATE INDEX idx_placement_attempt_answers_attempt ON placement_attempt_answers (placement_attempt_id);

CREATE TABLE placement_attempt_answer_selected_options (
    placement_attempt_answer_id UUID NOT NULL REFERENCES placement_attempt_answers(id) ON DELETE CASCADE,
    option_id                   UUID NOT NULL
);
CREATE INDEX idx_placement_attempt_answer_selected_options_answer
    ON placement_attempt_answer_selected_options (placement_attempt_answer_id);
