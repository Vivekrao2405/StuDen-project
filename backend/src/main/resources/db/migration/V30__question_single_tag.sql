-- Question Bank single-tag refactor: each question now carries EXACTLY ONE hierarchical tag
-- string (e.g. "python-sets-operators") instead of a set of independent tags in question_tags.
-- Existing multi-tagged questions are collapsed onto their most specific tag (longest string,
-- alphabetical tie-break) so no existing tagging signal is silently dropped.
ALTER TABLE questions ADD COLUMN tag VARCHAR(150);

UPDATE questions q
SET tag = ranked.tag
FROM (
    SELECT DISTINCT ON (question_id) question_id, tag
    FROM question_tags
    ORDER BY question_id, length(tag) DESC, tag ASC
) ranked
WHERE q.id = ranked.question_id;

DROP TABLE question_tags;
