-- Phase 2: a student-entered target company that isn't in the admin-managed placement_companies
-- catalog ("Can't find your company? -> Add company manually"). Deliberately its own table, owned
-- entirely by the profile that typed it: a manual name must never be promoted into the shared,
-- admin-verified placement_companies catalog, and must never leak into another student's profile.
CREATE TABLE placement_profile_manual_companies (
    id            UUID PRIMARY KEY,
    profile_id    UUID NOT NULL REFERENCES placement_profiles(id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_placement_profile_manual_company UNIQUE (profile_id, name)
);

CREATE INDEX idx_placement_profile_manual_companies_profile ON placement_profile_manual_companies (profile_id);
