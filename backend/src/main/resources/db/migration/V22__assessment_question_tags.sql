-- Tag-wise assessment analysis fix. Snapshots Question.tags onto each assessment_questions row at
-- generation time, mirroring question_tags (V16)'s shape and the topic_id/topic_name snapshot
-- pattern (V18): a question's tags may be edited by an admin after an assessment has already used
-- it, and historical results must stay stable, so live Question.tags is never read during scoring
-- (see SkillResultService.buildSummary — reads only this snapshot table).
CREATE TABLE assessment_question_tags (
    assessment_question_id  UUID NOT NULL REFERENCES assessment_questions(id) ON DELETE CASCADE,
    tag                     VARCHAR(100) NOT NULL,
    PRIMARY KEY (assessment_question_id, tag)
);
