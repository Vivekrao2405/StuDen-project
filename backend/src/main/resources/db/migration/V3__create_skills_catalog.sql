CREATE TABLE skills (
    id               UUID PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    normalized_name  VARCHAR(100) NOT NULL,
    category         VARCHAR(50)  NOT NULL,
    icon_slug        VARCHAR(50),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_skills_name ON skills (name);
CREATE UNIQUE INDEX uk_skills_normalized_name ON skills (normalized_name);
CREATE INDEX idx_skills_category ON skills (category);

CREATE TABLE skill_aliases (
    skill_id  UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    alias     VARCHAR(100) NOT NULL
);

CREATE INDEX idx_skill_aliases_skill_id ON skill_aliases (skill_id);
CREATE INDEX idx_skill_aliases_alias ON skill_aliases (lower(alias));

CREATE TABLE portfolio_skills (
    portfolio_id  UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    skill_id      UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (portfolio_id, skill_id)
);

CREATE INDEX idx_portfolio_skills_skill_id ON portfolio_skills (skill_id);

CREATE TABLE portfolio_availability_options (
    portfolio_id  UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    option        VARCHAR(50) NOT NULL,
    PRIMARY KEY (portfolio_id, option)
);

-- Seed catalog. iconSlug refers to a simple-icons.org slug; the frontend falls back to a
-- generic icon whenever a slug is null or the icon fails to load, so exact slug coverage
-- is best-effort, not load-bearing.
INSERT INTO skills (id, name, normalized_name, category, icon_slug) VALUES
    (gen_random_uuid(), 'React', 'react', 'Frontend', 'react'),
    (gen_random_uuid(), 'React Native', 'react native', 'Mobile', 'react'),
    (gen_random_uuid(), 'Vue.js', 'vue.js', 'Frontend', 'vuedotjs'),
    (gen_random_uuid(), 'Angular', 'angular', 'Frontend', 'angular'),
    (gen_random_uuid(), 'Next.js', 'next.js', 'Frontend', 'nextdotjs'),
    (gen_random_uuid(), 'HTML', 'html', 'Frontend', 'html5'),
    (gen_random_uuid(), 'CSS', 'css', 'Frontend', 'css3'),
    (gen_random_uuid(), 'Tailwind CSS', 'tailwind css', 'Frontend', 'tailwindcss'),
    (gen_random_uuid(), 'Bootstrap', 'bootstrap', 'Frontend', 'bootstrap'),
    (gen_random_uuid(), 'Redux', 'redux', 'Frontend', 'redux'),
    (gen_random_uuid(), 'Svelte', 'svelte', 'Frontend', 'svelte'),

    (gen_random_uuid(), 'JavaScript', 'javascript', 'Programming', 'javascript'),
    (gen_random_uuid(), 'TypeScript', 'typescript', 'Programming', 'typescript'),
    (gen_random_uuid(), 'Python', 'python', 'Programming', 'python'),
    (gen_random_uuid(), 'Java', 'java', 'Programming', 'openjdk'),
    (gen_random_uuid(), 'C++', 'c++', 'Programming', 'cplusplus'),
    (gen_random_uuid(), 'C', 'c', 'Programming', 'c'),
    (gen_random_uuid(), 'C#', 'c#', 'Programming', 'csharp'),
    (gen_random_uuid(), 'Go', 'go', 'Programming', 'go'),
    (gen_random_uuid(), 'Rust', 'rust', 'Programming', 'rust'),
    (gen_random_uuid(), 'PHP', 'php', 'Programming', 'php'),
    (gen_random_uuid(), 'Ruby', 'ruby', 'Programming', 'ruby'),
    (gen_random_uuid(), 'R', 'r', 'Programming', 'r'),

    (gen_random_uuid(), 'Node.js', 'node.js', 'Backend', 'nodedotjs'),
    (gen_random_uuid(), 'Express.js', 'express.js', 'Backend', 'express'),
    (gen_random_uuid(), 'Spring Boot', 'spring boot', 'Backend', 'springboot'),
    (gen_random_uuid(), 'Django', 'django', 'Backend', 'django'),
    (gen_random_uuid(), 'Flask', 'flask', 'Backend', 'flask'),
    (gen_random_uuid(), 'Jakarta EE', 'jakarta ee', 'Backend', 'jakartaee'),
    (gen_random_uuid(), 'Ruby on Rails', 'ruby on rails', 'Backend', 'rubyonrails'),
    (gen_random_uuid(), 'GraphQL', 'graphql', 'Backend', 'graphql'),
    (gen_random_uuid(), 'REST API', 'rest api', 'Backend', NULL),

    (gen_random_uuid(), 'SQL', 'sql', 'Database', NULL),
    (gen_random_uuid(), 'MySQL', 'mysql', 'Database', 'mysql'),
    (gen_random_uuid(), 'PostgreSQL', 'postgresql', 'Database', 'postgresql'),
    (gen_random_uuid(), 'MongoDB', 'mongodb', 'Database', 'mongodb'),
    (gen_random_uuid(), 'Redis', 'redis', 'Database', 'redis'),
    (gen_random_uuid(), 'SQLite', 'sqlite', 'Database', 'sqlite'),
    (gen_random_uuid(), 'Firebase', 'firebase', 'Database', 'firebase'),
    (gen_random_uuid(), 'Oracle Database', 'oracle database', 'Database', 'oracle'),

    (gen_random_uuid(), 'Power BI', 'power bi', 'Data/Analytics', 'powerbi'),
    (gen_random_uuid(), 'Excel', 'excel', 'Data/Analytics', 'microsoftexcel'),
    (gen_random_uuid(), 'Tableau', 'tableau', 'Data/Analytics', 'tableau'),
    (gen_random_uuid(), 'Pandas', 'pandas', 'Data/Analytics', 'pandas'),
    (gen_random_uuid(), 'NumPy', 'numpy', 'Data/Analytics', 'numpy'),
    (gen_random_uuid(), 'Data Analysis', 'data analysis', 'Data/Analytics', NULL),

    (gen_random_uuid(), 'Machine Learning', 'machine learning', 'AI/ML', NULL),
    (gen_random_uuid(), 'TensorFlow', 'tensorflow', 'AI/ML', 'tensorflow'),
    (gen_random_uuid(), 'PyTorch', 'pytorch', 'AI/ML', 'pytorch'),
    (gen_random_uuid(), 'Scikit-learn', 'scikit-learn', 'AI/ML', 'scikitlearn'),

    (gen_random_uuid(), 'Figma', 'figma', 'Design', 'figma'),
    (gen_random_uuid(), 'Adobe Photoshop', 'adobe photoshop', 'Design', 'adobephotoshop'),
    (gen_random_uuid(), 'Adobe Illustrator', 'adobe illustrator', 'Design', 'adobeillustrator'),
    (gen_random_uuid(), 'UI/UX Design', 'ui/ux design', 'Design', NULL),
    (gen_random_uuid(), 'Graphic Design', 'graphic design', 'Design', NULL),
    (gen_random_uuid(), 'Canva', 'canva', 'Design', 'canva'),

    (gen_random_uuid(), 'Flutter', 'flutter', 'Mobile', 'flutter'),
    (gen_random_uuid(), 'Kotlin', 'kotlin', 'Mobile', 'kotlin'),
    (gen_random_uuid(), 'Swift', 'swift', 'Mobile', 'swift'),
    (gen_random_uuid(), 'Dart', 'dart', 'Mobile', 'dart'),

    (gen_random_uuid(), 'Docker', 'docker', 'DevOps/Cloud', 'docker'),
    (gen_random_uuid(), 'Kubernetes', 'kubernetes', 'DevOps/Cloud', 'kubernetes'),
    (gen_random_uuid(), 'AWS', 'aws', 'DevOps/Cloud', 'amazonaws'),
    (gen_random_uuid(), 'Azure', 'azure', 'DevOps/Cloud', 'microsoftazure'),
    (gen_random_uuid(), 'Google Cloud', 'google cloud', 'DevOps/Cloud', 'googlecloud'),
    (gen_random_uuid(), 'Jenkins', 'jenkins', 'DevOps/Cloud', 'jenkins'),
    (gen_random_uuid(), 'Git', 'git', 'DevOps/Cloud', 'git'),
    (gen_random_uuid(), 'GitHub', 'github', 'DevOps/Cloud', 'github'),
    (gen_random_uuid(), 'GitLab', 'gitlab', 'DevOps/Cloud', 'gitlab'),
    (gen_random_uuid(), 'Linux', 'linux', 'DevOps/Cloud', 'linux'),
    (gen_random_uuid(), 'CI/CD', 'ci/cd', 'DevOps/Cloud', NULL),
    (gen_random_uuid(), 'Vercel', 'vercel', 'DevOps/Cloud', 'vercel'),
    (gen_random_uuid(), 'Nginx', 'nginx', 'DevOps/Cloud', 'nginx'),

    (gen_random_uuid(), 'Cybersecurity', 'cybersecurity', 'Cybersecurity', NULL),
    (gen_random_uuid(), 'Networking', 'networking', 'Cybersecurity', NULL),
    (gen_random_uuid(), 'Ethical Hacking', 'ethical hacking', 'Cybersecurity', NULL),

    (gen_random_uuid(), 'Video Editing', 'video editing', 'Creative', NULL),
    (gen_random_uuid(), 'Photography', 'photography', 'Creative', NULL),
    (gen_random_uuid(), 'Content Writing', 'content writing', 'Creative', NULL),
    (gen_random_uuid(), 'Public Speaking', 'public speaking', 'Soft Skills', NULL),
    (gen_random_uuid(), 'Tutoring', 'tutoring', 'Education', NULL);

INSERT INTO skill_aliases (skill_id, alias)
SELECT id, alias FROM skills, (VALUES
    ('React', 'React.js'), ('React', 'ReactJS'),
    ('JavaScript', 'JS'),
    ('TypeScript', 'TS'),
    ('C++', 'CPP'),
    ('C#', 'CSharp'),
    ('Go', 'Golang'),
    ('Node.js', 'NodeJS'), ('Node.js', 'Node'),
    ('Express.js', 'ExpressJS'), ('Express.js', 'Express'),
    ('Spring Boot', 'Spring'),
    ('Jakarta EE', 'Java EE'), ('Jakarta EE', 'JavaEE'),
    ('Ruby on Rails', 'Rails'),
    ('PostgreSQL', 'Postgres'),
    ('Power BI', 'PowerBI'),
    ('Excel', 'Microsoft Excel'),
    ('Machine Learning', 'ML'),
    ('UI/UX Design', 'UX'), ('UI/UX Design', 'UI Design'), ('UI/UX Design', 'UX Design'),
    ('AWS', 'Amazon Web Services'),
    ('Azure', 'Microsoft Azure'),
    ('Google Cloud', 'GCP'),
    ('Vue.js', 'Vue'),
    ('Next.js', 'NextJS')
) AS a(skill_name, alias)
WHERE skills.name = a.skill_name;
