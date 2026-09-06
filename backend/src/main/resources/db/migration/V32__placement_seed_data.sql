-- Placement Phase 0 seed data, following the same in-migration seeding mechanism the skills
-- catalog itself uses (V3/V4). Purely additive and idempotent-by-construction: every INSERT is
-- guarded, so re-running against a database that already has these rows changes nothing and no
-- existing row is ever updated or deleted.
--
-- Only the roles, skills and role->skill mappings the spec actually lists are seeded. No
-- companies, no series and no company-specific preparation content: the spec is explicit that
-- company preparation must use verified content and must not invent claims about what a given
-- company asks, so those stay admin-authored.
--
-- Question types: the spec lists MCQ / Coding / Aptitude / Technical / Case Study. In this
-- codebase those are NOT one enum. MCQ-shaped items (MCQ, aptitude, technical, case study) are
-- rows in `questions`, categorised by the skill + hierarchical tag the Question Bank already
-- uses -- hence the "Aptitude" skill seeded below. Coding items are `practical_assessments`,
-- which already own supported languages, starter code, public/hidden test cases and time/memory
-- limits. Adding CODING to com.studen.questionbank.QuestionType would have created a second,
-- weaker copy of infrastructure that already exists, so it was deliberately not done.

-- ---------------------------------------------------------------------------------------------
-- Skills the spec requires that the universal catalog did not already carry. Everything else it
-- names (SQL, Python, Pandas, NumPy, Excel, Statistics, Power BI, Data Visualization, Business
-- Analysis, ...) already exists from V3/V4 and is reused as-is.
-- ---------------------------------------------------------------------------------------------
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type)
SELECT gen_random_uuid(), v.name, v.normalized_name, v.category, v.icon_slug, 'LUCIDE'
FROM (VALUES
    ('Programming Fundamentals', 'programming fundamentals', 'Programming',       'Code2'),
    ('DSA',                      'dsa',                      'Programming',       'Binary'),
    ('OOP',                      'oop',                      'Programming',       'Boxes'),
    ('DBMS',                     'dbms',                     'Database',          'Database'),
    ('Operating Systems',        'operating systems',        'Computer Science',  'Cpu'),
    ('Computer Networks',        'computer networks',        'Computer Science',  'Network'),
    ('Aptitude',                 'aptitude',                 'Career Skills',     'Calculator')
) AS v(name, normalized_name, category, icon_slug)
WHERE NOT EXISTS (SELECT 1 FROM skills s WHERE s.normalized_name = v.normalized_name);

-- Aliases so the existing skill search finds these under the names students actually type.
INSERT INTO skill_aliases (skill_id, alias)
SELECT s.id, v.alias
FROM (VALUES
    ('dsa',               'Data Structures and Algorithms'),
    ('dsa',               'Data Structures'),
    ('dsa',               'Algorithms'),
    ('oop',               'Object Oriented Programming'),
    ('dbms',              'Database Management Systems'),
    ('operating systems', 'OS'),
    ('computer networks', 'Networks'),
    ('aptitude',          'Quantitative Aptitude'),
    ('aptitude',          'Logical Reasoning')
) AS v(normalized_name, alias)
JOIN skills s ON s.normalized_name = v.normalized_name
WHERE NOT EXISTS (
    SELECT 1 FROM skill_aliases a WHERE a.skill_id = s.id AND a.alias = v.alias
);

-- ---------------------------------------------------------------------------------------------
-- Roles (spec Phase 1)
-- ---------------------------------------------------------------------------------------------
INSERT INTO placement_roles (id, name, normalized_name, description, status, display_order)
SELECT gen_random_uuid(), v.name, v.normalized_name, v.description, 'ACTIVE', v.display_order
FROM (VALUES
    ('Software Developer / SDE', 'software developer / sde',
     'Builds and ships software: programming fundamentals, data structures and algorithms, core CS subjects and aptitude.', 1),
    ('Data Analyst', 'data analyst',
     'Turns data into decisions with SQL, Python, spreadsheets, statistics and BI dashboards.', 2),
    ('Data Scientist', 'data scientist',
     'Builds statistical and machine-learning models on top of strong Python and mathematics foundations.', 3),
    ('Frontend Developer', 'frontend developer',
     'Builds user-facing web interfaces with HTML, CSS, JavaScript and a modern component framework.', 4),
    ('Backend Developer', 'backend developer',
     'Builds server-side services, APIs and data layers.', 5),
    ('Cloud / DevOps', 'cloud / devops',
     'Runs and automates infrastructure, deployment pipelines and cloud platforms.', 6),
    ('Cybersecurity', 'cybersecurity',
     'Secures systems and networks, covering security fundamentals, networking and ethical hacking.', 7)
) AS v(name, normalized_name, description, display_order)
WHERE NOT EXISTS (SELECT 1 FROM placement_roles r WHERE r.normalized_name = v.normalized_name);

-- ---------------------------------------------------------------------------------------------
-- Role -> Skill mappings. weight/priority are starting values an admin can retune from Phase 1
-- onward; priority 1 is the highest. required_proficiency is left NULL on purpose -- the spec
-- does not state target levels, and inventing them would put unsupported judgements in the data.
--
-- The SDE and Data Analyst mappings are exactly the two the spec lists. The remaining five roles
-- get a small mapping built only from skills the spec itself names elsewhere or that already
-- exist in the catalog, so no role ships with an empty skill list.
-- ---------------------------------------------------------------------------------------------
INSERT INTO placement_role_skills (id, role_id, skill_id, weight, priority)
SELECT gen_random_uuid(), r.id, s.id, v.weight, v.priority
FROM (VALUES
    -- SDE
    ('software developer / sde', 'programming fundamentals', 5, 1),
    ('software developer / sde', 'dsa',                      5, 2),
    ('software developer / sde', 'oop',                      4, 3),
    ('software developer / sde', 'sql',                      3, 4),
    ('software developer / sde', 'dbms',                     3, 5),
    ('software developer / sde', 'operating systems',        3, 6),
    ('software developer / sde', 'computer networks',        3, 7),
    ('software developer / sde', 'aptitude',                 2, 8),

    -- Data Analyst
    ('data analyst', 'sql',                 5, 1),
    ('data analyst', 'python',              5, 2),
    ('data analyst', 'pandas',              4, 3),
    ('data analyst', 'numpy',               3, 4),
    ('data analyst', 'excel',               4, 5),
    ('data analyst', 'statistics',          4, 6),
    ('data analyst', 'power bi',            4, 7),
    ('data analyst', 'data visualization',  3, 8),
    ('data analyst', 'business analysis',   2, 9),

    -- Data Scientist
    ('data scientist', 'python',            5, 1),
    ('data scientist', 'statistics',        5, 2),
    ('data scientist', 'pandas',            4, 3),
    ('data scientist', 'numpy',             4, 4),
    ('data scientist', 'machine learning',  5, 5),
    ('data scientist', 'sql',               3, 6),
    ('data scientist', 'data visualization', 3, 7),

    -- Frontend Developer
    ('frontend developer', 'html',        4, 1),
    ('frontend developer', 'css',         4, 2),
    ('frontend developer', 'javascript',  5, 3),
    ('frontend developer', 'react',       4, 4),
    ('frontend developer', 'typescript',  3, 5),
    ('frontend developer', 'ui/ux design', 2, 6),

    -- Backend Developer
    ('backend developer', 'programming fundamentals', 4, 1),
    ('backend developer', 'oop',                      4, 2),
    ('backend developer', 'sql',                      5, 3),
    ('backend developer', 'dbms',                     4, 4),
    ('backend developer', 'rest api',                 4, 5),
    ('backend developer', 'dsa',                      3, 6),

    -- Cloud / DevOps
    ('cloud / devops', 'linux',      5, 1),
    ('cloud / devops', 'docker',     5, 2),
    ('cloud / devops', 'kubernetes', 4, 3),
    ('cloud / devops', 'ci/cd',      4, 4),
    ('cloud / devops', 'aws',        4, 5),
    ('cloud / devops', 'git',        3, 6),

    -- Cybersecurity
    ('cybersecurity', 'cybersecurity',      5, 1),
    ('cybersecurity', 'computer networks',  5, 2),
    ('cybersecurity', 'linux',              4, 3),
    ('cybersecurity', 'ethical hacking',    4, 4),
    ('cybersecurity', 'operating systems',  3, 5)
) AS v(role_normalized_name, skill_normalized_name, weight, priority)
JOIN placement_roles r ON r.normalized_name = v.role_normalized_name
JOIN skills s ON s.normalized_name = v.skill_normalized_name
WHERE NOT EXISTS (
    SELECT 1 FROM placement_role_skills rs WHERE rs.role_id = r.id AND rs.skill_id = s.id
);
