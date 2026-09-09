-- Placement System Phase 3 completion — lets a readiness assessment mix Question Bank (MCQ/
-- aptitude/technical/case-study) items with existing PracticalAssessment (coding/SQL/web/etc)
-- items, reusing com.studen.practical as-is (no second coding/execution system). Mirrors the
-- typed-nullable-FK + CHECK pattern placement_module_items (V31) already established for the
-- exact same "points at one of several existing content tables" problem, using the SAME
-- ModuleItemType discriminator (QUESTION / PRACTICAL_ASSESSMENT) rather than inventing a new one.
--
-- Purely additive: existing rows (all item_type='QUESTION' from Phase 3's initial MCQ-only cut)
-- remain valid and unaffected; question_id merely becomes nullable to make room for the
-- practical-assessment alternative.

-- ---------------------------------------------------------------------------------------------
-- Assessment-template side (admin-configured, shared across attempts)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE placement_assessment_questions
    ALTER COLUMN question_id DROP NOT NULL,
    ADD COLUMN item_type VARCHAR(30) NOT NULL DEFAULT 'QUESTION',
    ADD COLUMN practical_assessment_id UUID REFERENCES practical_assessments(id);

ALTER TABLE placement_assessment_questions
    ADD CONSTRAINT ck_placement_assessment_question_target CHECK (
        (item_type = 'QUESTION' AND question_id IS NOT NULL AND practical_assessment_id IS NULL)
     OR (item_type = 'PRACTICAL_ASSESSMENT' AND question_id IS NULL AND practical_assessment_id IS NOT NULL)
    );

-- Replace the old (assessment, question) unique constraint with two partial uniques -- one per
-- target column -- since question_id is no longer always populated (a plain UNIQUE constraint
-- would let every NULL question_id row past uniqueness anyway, but being explicit here matches
-- placement_module_items' own convention and keeps both content kinds equally de-duplicated).
ALTER TABLE placement_assessment_questions DROP CONSTRAINT uq_placement_assessment_question;
CREATE UNIQUE INDEX uk_placement_assessment_question_question
    ON placement_assessment_questions (placement_assessment_id, question_id) WHERE question_id IS NOT NULL;
CREATE UNIQUE INDEX uk_placement_assessment_question_practical
    ON placement_assessment_questions (placement_assessment_id, practical_assessment_id) WHERE practical_assessment_id IS NOT NULL;

CREATE INDEX idx_placement_assessment_questions_practical ON placement_assessment_questions (practical_assessment_id);

-- ---------------------------------------------------------------------------------------------
-- Attempt side: per-attempt snapshot (see V34) plus the live link to the actual PracticalAttempt
-- a practical slot delegates to -- the existing com.studen.practical engine runs and scores it;
-- this column just remembers which one belongs to which slot.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE placement_attempt_questions
    ALTER COLUMN question_id DROP NOT NULL,
    ADD COLUMN item_type VARCHAR(30) NOT NULL DEFAULT 'QUESTION',
    ADD COLUMN practical_assessment_id UUID REFERENCES practical_assessments(id),
    ADD COLUMN practical_attempt_id UUID REFERENCES practical_attempts(id);

ALTER TABLE placement_attempt_questions
    ADD CONSTRAINT ck_placement_attempt_question_target CHECK (
        (item_type = 'QUESTION' AND question_id IS NOT NULL AND practical_assessment_id IS NULL)
     OR (item_type = 'PRACTICAL_ASSESSMENT' AND question_id IS NULL AND practical_assessment_id IS NOT NULL)
    );

CREATE INDEX idx_placement_attempt_questions_practical_attempt
    ON placement_attempt_questions (practical_attempt_id);
