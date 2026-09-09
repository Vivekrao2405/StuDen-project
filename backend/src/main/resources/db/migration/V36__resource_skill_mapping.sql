-- Phase 4 (Placement -> My Learning integration): lets an admin map a resource to additional
-- skills beyond its existing mandatory `resources.skill_id` primary skill, so one resource (e.g.
-- "Complete Data Analysis with Python") can be recommended for several distinct Placement skill
-- gaps (Python, Pandas, NumPy) without duplicating the resource row. The primary skill_id already
-- counts as "mapped" for matching purposes -- this table only holds the *additional* skills.
CREATE TABLE resource_skills (
    resource_id UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES skills(id),
    PRIMARY KEY (resource_id, skill_id)
);
CREATE INDEX idx_resource_skills_skill_id ON resource_skills (skill_id);
