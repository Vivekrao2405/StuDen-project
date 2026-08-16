-- Phase 7.3: skill scoring / topic performance. Snapshots the topic name onto each
-- assessment_questions row at generation time. Question content is already immutable once
-- PUBLISHED (Phase 7.1 versioning forks a new row on edit), but a Topic row itself has no such
-- lock — without this snapshot, a future topic rename would retroactively change historical topic
-- performance for every past assessment. No FK on topic_id (deliberately): a historical result
-- must stay readable even if topic management grows a delete path later.
ALTER TABLE assessment_questions ADD COLUMN topic_id UUID;
ALTER TABLE assessment_questions ADD COLUMN topic_name VARCHAR(255);
