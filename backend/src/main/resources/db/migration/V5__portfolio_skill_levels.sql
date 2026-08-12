-- Per-portfolio proficiency level for an attached skill. Kept as its own table (rather than a
-- column on the existing portfolio_skills join table) so the already-shipped skill-attachment
-- flow (create/update portfolio, SkillPicker) is completely unaffected — a portfolio's skill list
-- is unchanged, this is a separate, optional overlay looked up only when building the response.
CREATE TABLE portfolio_skill_levels (
    id            UUID PRIMARY KEY,
    portfolio_id  UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    skill_id      UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    level         VARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_portfolio_skill_levels_portfolio_skill UNIQUE (portfolio_id, skill_id)
);

CREATE INDEX idx_portfolio_skill_levels_portfolio_id ON portfolio_skill_levels (portfolio_id);
