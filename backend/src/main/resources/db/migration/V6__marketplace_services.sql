-- Marketplace service foundation (Phase 6.1). Discovery only — no pricing, booking, or order
-- fields; those belong to a later phase once service creation itself is built. Applies to every
-- environment (schema, not data) — only development seed rows are gated behind the "dev" profile.
CREATE TABLE services (
    id            UUID PRIMARY KEY,
    portfolio_id  UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    category      VARCHAR(30) NOT NULL,
    location      VARCHAR(255),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_services_portfolio_id ON services (portfolio_id);
CREATE INDEX idx_services_category ON services (category);
CREATE INDEX idx_services_status ON services (status);

CREATE TABLE service_skills (
    service_id  UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    skill_id    UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (service_id, skill_id)
);

CREATE INDEX idx_service_skills_skill_id ON service_skills (skill_id);
