-- Adds icon_type so a skill's icon can be either a simple-icons brand logo (BRAND) or a
-- lucide-react generic icon (LUCIDE) — StuDen spans far more than software/tech skills, so most
-- new skills below use a generic icon rather than assuming an official logo exists.
ALTER TABLE skills ADD COLUMN icon_type VARCHAR(20) NOT NULL DEFAULT 'LUCIDE';

-- Every skill seeded in V3 with a non-null icon_slug used a simple-icons brand slug.
UPDATE skills SET icon_type = 'BRAND' WHERE icon_slug IS NOT NULL;

-- V3 left a handful of skills without an icon at all (no good brand logo existed for them).
-- Give each a specific, verified-to-exist lucide-react icon instead of a generic placeholder.
UPDATE skills SET icon_slug = 'Network' WHERE name = 'REST API';
UPDATE skills SET icon_slug = 'Database' WHERE name = 'SQL';
UPDATE skills SET icon_slug = 'BarChart3' WHERE name = 'Data Analysis';
UPDATE skills SET icon_slug = 'Brain' WHERE name = 'Machine Learning';
UPDATE skills SET icon_slug = 'Palette' WHERE name = 'UI/UX Design';
UPDATE skills SET icon_slug = 'Brush' WHERE name = 'Graphic Design';
UPDATE skills SET icon_slug = 'Server' WHERE name = 'CI/CD';
UPDATE skills SET icon_slug = 'ShieldCheck' WHERE name = 'Cybersecurity';
UPDATE skills SET icon_slug = 'Network' WHERE name = 'Networking';
UPDATE skills SET icon_slug = 'ShieldAlert' WHERE name = 'Ethical Hacking';
UPDATE skills SET icon_slug = 'Clapperboard' WHERE name = 'Video Editing';
UPDATE skills SET icon_slug = 'Camera' WHERE name = 'Photography';
UPDATE skills SET icon_slug = 'PenTool' WHERE name = 'Content Writing';
UPDATE skills SET icon_slug = 'Mic' WHERE name = 'Public Speaking';
UPDATE skills SET icon_slug = 'GraduationCap' WHERE name = 'Tutoring';

-- Reclassify a few V3 categories into the broader, clearer taxonomy used going forward.
UPDATE skills SET category = 'Photography & Video' WHERE category = 'Creative';
UPDATE skills SET category = 'Leadership' WHERE category = 'Soft Skills';
UPDATE skills SET category = 'Teaching' WHERE category = 'Education';
UPDATE skills SET category = 'Writing' WHERE name = 'Content Writing';

-- More Data/Analytics
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Statistics', 'statistics', 'Data/Analytics', 'Sigma', 'LUCIDE'),
    (gen_random_uuid(), 'Research', 'research', 'Data/Analytics', 'Microscope', 'LUCIDE'),
    (gen_random_uuid(), 'Data Visualization', 'data visualization', 'Data/Analytics', 'PieChart', 'LUCIDE'),
    (gen_random_uuid(), 'Google Analytics', 'google analytics', 'Data/Analytics', 'googleanalytics', 'BRAND');

-- More Design
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Illustration', 'illustration', 'Design', 'Brush', 'LUCIDE'),
    (gen_random_uuid(), 'Animation', 'animation', 'Design', 'Clapperboard', 'LUCIDE'),
    (gen_random_uuid(), '3D Design', '3d design', 'Design', 'Box', 'LUCIDE'),
    (gen_random_uuid(), 'UX Research', 'ux research', 'Design', 'Microscope', 'LUCIDE');

-- Photography & Video
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Photo Editing', 'photo editing', 'Photography & Video', 'Aperture', 'LUCIDE'),
    (gen_random_uuid(), 'Mobile Photography', 'mobile photography', 'Photography & Video', 'Camera', 'LUCIDE'),
    (gen_random_uuid(), 'Cinematography', 'cinematography', 'Photography & Video', 'Film', 'LUCIDE'),
    (gen_random_uuid(), 'Drone Photography', 'drone photography', 'Photography & Video', 'Camera', 'LUCIDE');

-- Writing & Communication
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Copywriting', 'copywriting', 'Writing', 'PenTool', 'LUCIDE'),
    (gen_random_uuid(), 'Technical Writing', 'technical writing', 'Writing', 'FileText', 'LUCIDE'),
    (gen_random_uuid(), 'Academic Writing', 'academic writing', 'Writing', 'BookText', 'LUCIDE'),
    (gen_random_uuid(), 'Creative Writing', 'creative writing', 'Writing', 'Feather', 'LUCIDE'),
    (gen_random_uuid(), 'Blogging', 'blogging', 'Writing', 'NotebookPen', 'LUCIDE'),
    (gen_random_uuid(), 'Storytelling', 'storytelling', 'Writing', 'BookOpen', 'LUCIDE'),
    (gen_random_uuid(), 'Editing & Proofreading', 'editing & proofreading', 'Writing', 'ScrollText', 'LUCIDE');

-- Leadership & People Skills (Public Speaking already reclassified above)
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Leadership', 'leadership', 'Leadership', 'Award', 'LUCIDE'),
    (gen_random_uuid(), 'Teamwork', 'teamwork', 'Leadership', 'Users2', 'LUCIDE'),
    (gen_random_uuid(), 'Negotiation', 'negotiation', 'Leadership', 'Handshake', 'LUCIDE'),
    (gen_random_uuid(), 'Communication', 'communication', 'Leadership', 'MessageCircle', 'LUCIDE'),
    (gen_random_uuid(), 'Presentation Skills', 'presentation skills', 'Leadership', 'Presentation', 'LUCIDE'),
    (gen_random_uuid(), 'Mentoring', 'mentoring', 'Leadership', 'UserCheck', 'LUCIDE'),
    (gen_random_uuid(), 'Event Management', 'event management', 'Leadership', 'Calendar', 'LUCIDE');

-- Teaching & Education (Tutoring already reclassified above)
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Teaching', 'teaching', 'Teaching', 'GraduationCap', 'LUCIDE'),
    (gen_random_uuid(), 'Curriculum Development', 'curriculum development', 'Teaching', 'ClipboardList', 'LUCIDE'),
    (gen_random_uuid(), 'Workshop Facilitation', 'workshop facilitation', 'Teaching', 'Presentation', 'LUCIDE'),
    (gen_random_uuid(), 'Academic Coaching', 'academic coaching', 'Teaching', 'BookOpen', 'LUCIDE');

-- Business & Management
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Marketing', 'marketing', 'Business', 'Megaphone', 'LUCIDE'),
    (gen_random_uuid(), 'Digital Marketing', 'digital marketing', 'Business', 'TrendingUp', 'LUCIDE'),
    (gen_random_uuid(), 'Social Media Marketing', 'social media marketing', 'Business', 'Megaphone', 'LUCIDE'),
    (gen_random_uuid(), 'Market Research', 'market research', 'Business', 'LineChart', 'LUCIDE'),
    (gen_random_uuid(), 'Sales', 'sales', 'Business', 'DollarSign', 'LUCIDE'),
    (gen_random_uuid(), 'Business Analysis', 'business analysis', 'Business', 'BarChart3', 'LUCIDE'),
    (gen_random_uuid(), 'Entrepreneurship', 'entrepreneurship', 'Business', 'Rocket', 'LUCIDE'),
    (gen_random_uuid(), 'Project Management', 'project management', 'Business', 'ClipboardCheck', 'LUCIDE'),
    (gen_random_uuid(), 'Finance', 'finance', 'Business', 'CreditCard', 'LUCIDE'),
    (gen_random_uuid(), 'Accounting', 'accounting', 'Business', 'FileSpreadsheet', 'LUCIDE'),
    (gen_random_uuid(), 'Operations Management', 'operations management', 'Business', 'Briefcase', 'LUCIDE'),
    (gen_random_uuid(), 'Business Strategy', 'business strategy', 'Business', 'Target', 'LUCIDE');

-- Media & Content
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Content Creation', 'content creation', 'Media & Content', 'Sparkles', 'LUCIDE'),
    (gen_random_uuid(), 'YouTube', 'youtube', 'Media & Content', 'youtube', 'BRAND'),
    (gen_random_uuid(), 'Podcasting', 'podcasting', 'Media & Content', 'Podcast', 'LUCIDE'),
    (gen_random_uuid(), 'Social Media Management', 'social media management', 'Media & Content', 'MessageCircle', 'LUCIDE'),
    (gen_random_uuid(), 'Script Writing', 'script writing', 'Media & Content', 'FileText', 'LUCIDE'),
    (gen_random_uuid(), 'Vlogging', 'vlogging', 'Media & Content', 'Video', 'LUCIDE'),
    (gen_random_uuid(), 'Live Streaming', 'live streaming', 'Media & Content', 'Radio', 'LUCIDE');

-- Arts & Performance
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Singing', 'singing', 'Arts & Performance', 'Mic2', 'LUCIDE'),
    (gen_random_uuid(), 'Guitar', 'guitar', 'Arts & Performance', 'Guitar', 'LUCIDE'),
    (gen_random_uuid(), 'Guitar Performance', 'guitar performance', 'Arts & Performance', 'Guitar', 'LUCIDE'),
    (gen_random_uuid(), 'Piano', 'piano', 'Arts & Performance', 'Piano', 'LUCIDE'),
    (gen_random_uuid(), 'Music Production', 'music production', 'Arts & Performance', 'Music2', 'LUCIDE'),
    (gen_random_uuid(), 'Dance', 'dance', 'Arts & Performance', 'Music3', 'LUCIDE'),
    (gen_random_uuid(), 'Acting', 'acting', 'Arts & Performance', 'Drama', 'LUCIDE'),
    (gen_random_uuid(), 'Theatre', 'theatre', 'Arts & Performance', 'Drama', 'LUCIDE'),
    (gen_random_uuid(), 'Painting', 'painting', 'Arts & Performance', 'Palette', 'LUCIDE'),
    (gen_random_uuid(), 'Drawing', 'drawing', 'Arts & Performance', 'PenTool', 'LUCIDE');

-- Sports & Fitness
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Football', 'football', 'Sports & Fitness', 'Trophy', 'LUCIDE'),
    (gen_random_uuid(), 'Football Coaching', 'football coaching', 'Sports & Fitness', 'Trophy', 'LUCIDE'),
    (gen_random_uuid(), 'Cricket', 'cricket', 'Sports & Fitness', 'Trophy', 'LUCIDE'),
    (gen_random_uuid(), 'Basketball', 'basketball', 'Sports & Fitness', 'Trophy', 'LUCIDE'),
    (gen_random_uuid(), 'Badminton', 'badminton', 'Sports & Fitness', 'Medal', 'LUCIDE'),
    (gen_random_uuid(), 'Tennis', 'tennis', 'Sports & Fitness', 'Medal', 'LUCIDE'),
    (gen_random_uuid(), 'Swimming', 'swimming', 'Sports & Fitness', 'Waves', 'LUCIDE'),
    (gen_random_uuid(), 'Athletics', 'athletics', 'Sports & Fitness', 'Activity', 'LUCIDE'),
    (gen_random_uuid(), 'Coaching', 'coaching', 'Sports & Fitness', 'Medal', 'LUCIDE'),
    (gen_random_uuid(), 'Fitness Training', 'fitness training', 'Sports & Fitness', 'Dumbbell', 'LUCIDE'),
    (gen_random_uuid(), 'Yoga', 'yoga', 'Sports & Fitness', 'PersonStanding', 'LUCIDE');

-- Academic & Research
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Mathematics', 'mathematics', 'Academic', 'Sigma', 'LUCIDE'),
    (gen_random_uuid(), 'Physics', 'physics', 'Academic', 'Atom', 'LUCIDE'),
    (gen_random_uuid(), 'Chemistry', 'chemistry', 'Academic', 'FlaskConical', 'LUCIDE'),
    (gen_random_uuid(), 'Biology', 'biology', 'Academic', 'Dna', 'LUCIDE'),
    (gen_random_uuid(), 'Academic Research', 'academic research', 'Academic', 'Telescope', 'LUCIDE');

-- Languages
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'English', 'english', 'Languages', 'Languages', 'LUCIDE'),
    (gen_random_uuid(), 'Hindi', 'hindi', 'Languages', 'Languages', 'LUCIDE'),
    (gen_random_uuid(), 'Telugu', 'telugu', 'Languages', 'Languages', 'LUCIDE'),
    (gen_random_uuid(), 'German', 'german', 'Languages', 'Languages', 'LUCIDE'),
    (gen_random_uuid(), 'French', 'french', 'Languages', 'Languages', 'LUCIDE'),
    (gen_random_uuid(), 'Spanish', 'spanish', 'Languages', 'Languages', 'LUCIDE'),
    (gen_random_uuid(), 'Translation', 'translation', 'Languages', 'Globe', 'LUCIDE'),
    (gen_random_uuid(), 'Interpretation', 'interpretation', 'Languages', 'Mic', 'LUCIDE');

-- Practical / Technical Skills
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Electronics', 'electronics', 'Practical Skills', 'CircuitBoard', 'LUCIDE'),
    (gen_random_uuid(), 'Robotics', 'robotics', 'Practical Skills', 'Cpu', 'LUCIDE'),
    (gen_random_uuid(), 'CAD Design', 'cad design', 'Practical Skills', 'Ruler', 'LUCIDE'),
    (gen_random_uuid(), '3D Printing', '3d printing', 'Practical Skills', 'Box', 'LUCIDE'),
    (gen_random_uuid(), 'Automotive Repair', 'automotive repair', 'Practical Skills', 'Wrench', 'LUCIDE'),
    (gen_random_uuid(), 'Welding', 'welding', 'Practical Skills', 'Hammer', 'LUCIDE'),
    (gen_random_uuid(), 'Carpentry', 'carpentry', 'Practical Skills', 'Hammer', 'LUCIDE'),
    (gen_random_uuid(), 'Cooking', 'cooking', 'Practical Skills', 'ChefHat', 'LUCIDE');

-- Professional / Career Skills
INSERT INTO skills (id, name, normalized_name, category, icon_slug, icon_type) VALUES
    (gen_random_uuid(), 'Resume Writing', 'resume writing', 'Career Skills', 'FileText', 'LUCIDE'),
    (gen_random_uuid(), 'Interview Preparation', 'interview preparation', 'Career Skills', 'Contact', 'LUCIDE'),
    (gen_random_uuid(), 'Career Coaching', 'career coaching', 'Career Skills', 'UserCheck', 'LUCIDE'),
    (gen_random_uuid(), 'Freelancing', 'freelancing', 'Career Skills', 'Briefcase', 'LUCIDE'),
    (gen_random_uuid(), 'Professional Networking', 'professional networking', 'Career Skills', 'Handshake', 'LUCIDE'),
    (gen_random_uuid(), 'Personal Branding', 'personal branding', 'Career Skills', 'IdCard', 'LUCIDE');

-- A handful of high-value aliases for the new skills, matching common shorthand/keywords a
-- student might actually type (mirrors the alias approach already used in V3).
INSERT INTO skill_aliases (skill_id, alias)
SELECT id, alias FROM skills, (VALUES
    ('Photography', 'Photo'),
    ('Photo Editing', 'Photoshop Editing'),
    ('Mathematics', 'Math'),
    ('Mathematics', 'Maths'),
    ('Public Speaking', 'Speaking'),
    ('Social Media Marketing', 'SMM'),
    ('Digital Marketing', 'SEO'),
    ('Project Management', 'PM'),
    ('Business Analysis', 'BA'),
    ('Football Coaching', 'Coaching'),
    ('Guitar Performance', 'Guitarist'),
    ('Interview Preparation', 'Interview Prep'),
    ('Curriculum Development', 'Course Design')
) AS a(skill_name, alias)
WHERE skills.name = a.skill_name;
