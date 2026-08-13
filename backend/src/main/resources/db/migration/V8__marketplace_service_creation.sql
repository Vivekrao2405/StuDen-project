-- Marketplace service creation/CRUD (Phase 6.3). V6 was discovery-only (title/description/
-- category/location/status); this adds pricing, delivery, availability, media, links,
-- deliverables, and reference-only links to the student's own showcase projects.
ALTER TABLE services
    ADD COLUMN price_amount   INT,
    ADD COLUMN currency       VARCHAR(10) NOT NULL DEFAULT 'INR',
    ADD COLUMN delivery_days  INT,
    ADD COLUMN available      BOOLEAN NOT NULL DEFAULT true;

-- New services now start as DRAFT (enforced at the application layer, see ServiceListingService)
-- rather than V6's original ACTIVE default — kept in sync here for documentation purposes, even
-- though Hibernate always sends the explicit field value on insert so this default is never
-- actually relied on by the app.
ALTER TABLE services ALTER COLUMN status SET DEFAULT 'DRAFT';

CREATE TABLE service_media (
    id             UUID PRIMARY KEY,
    service_id     UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    media_type     VARCHAR(10) NOT NULL,
    url            VARCHAR(1000) NOT NULL,
    thumbnail_url  VARCHAR(1000),
    public_id      VARCHAR(500) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0,
    is_cover       BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_media_service_id ON service_media (service_id);

CREATE TABLE service_links (
    service_id  UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    link_order  INT NOT NULL,
    label       VARCHAR(100) NOT NULL,
    url         VARCHAR(500) NOT NULL,
    PRIMARY KEY (service_id, link_order)
);

CREATE TABLE service_deliverables (
    service_id   UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    item_order   INT NOT NULL,
    description  VARCHAR(300) NOT NULL,
    PRIMARY KEY (service_id, item_order)
);

-- Reference-only join to the student's own showcase projects — never duplicates project data.
-- project_id cascades so a deleted Project just drops the link row (the service itself is
-- untouched); service_id cascades so a deleted Service never touches Project rows.
CREATE TABLE service_projects (
    service_id  UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    PRIMARY KEY (service_id, project_id)
);

CREATE INDEX idx_service_projects_project_id ON service_projects (project_id);
