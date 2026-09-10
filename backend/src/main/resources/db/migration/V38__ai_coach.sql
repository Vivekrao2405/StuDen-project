-- Phase 6: AI Coach — per-question, multi-turn coaching interactions against the OpenAI API.
-- Scoped to a single practical_attempt_question (Phase 7.6's per-question model), never the whole
-- attempt, so history/rate-limit queries and hint-level derivation stay question-scoped.

CREATE TABLE ai_coach_interactions (
    id                             UUID PRIMARY KEY,
    user_id                        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    practical_attempt_question_id  UUID NOT NULL REFERENCES practical_attempt_questions(id) ON DELETE CASCADE,
    action_type                    VARCHAR(30) NOT NULL,
    -- Only meaningful for action_type = HINT: 1st hint, 2nd hint, ... derived server-side from
    -- prior HINT rows for this (user, attemptQuestion) pair — never client-supplied.
    hint_level                     INT,
    -- Only meaningful for action_type = EXPLAIN_CONCEPT: the student's free-text topic.
    request_topic                  VARCHAR(300),
    response_text                  TEXT NOT NULL,
    model                          VARCHAR(50) NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- History panel + hint-level derivation (per question, per student, chronological).
CREATE INDEX idx_ai_coach_interactions_question_user_created
    ON ai_coach_interactions (practical_attempt_question_id, user_id, created_at);

-- Rate-limit window count: scans a user's recent rows across every question, so user_id must lead.
CREATE INDEX idx_ai_coach_interactions_user_created
    ON ai_coach_interactions (user_id, created_at);
