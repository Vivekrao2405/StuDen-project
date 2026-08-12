-- Showcase / Projects (Phase 6.2). A project belongs to a portfolio, can carry multiple ordered
-- media items (images/videos, stored on Cloudinary — only the URL/metadata lives here), skill
-- tags (reusing the existing catalog), and optional external links.
CREATE TABLE projects (
    id                 UUID PRIMARY KEY,
    portfolio_id       UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    title              VARCHAR(255) NOT NULL,
    short_description  VARCHAR(500),
    description        TEXT,
    visibility         VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_portfolio_id ON projects (portfolio_id);
CREATE INDEX idx_projects_visibility ON projects (visibility);

CREATE TABLE project_skills (
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, skill_id)
);

CREATE INDEX idx_project_skills_skill_id ON project_skills (skill_id);

CREATE TABLE project_media (
    id             UUID PRIMARY KEY,
    project_id     UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    media_type     VARCHAR(10) NOT NULL,
    url            VARCHAR(1000) NOT NULL,
    thumbnail_url  VARCHAR(1000),
    public_id      VARCHAR(500) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0,
    is_cover       BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_media_project_id ON project_media (project_id);

CREATE TABLE project_links (
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    link_order  INT NOT NULL,
    label       VARCHAR(100) NOT NULL,
    url         VARCHAR(500) NOT NULL,
    PRIMARY KEY (project_id, link_order)
);
