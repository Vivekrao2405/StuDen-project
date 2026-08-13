-- Phase 6.5: a StuDen user requesting another student's ACTIVE+available service. `service_id`
-- is nullable with ON DELETE SET NULL (not CASCADE, unlike most child tables in this schema) —
-- ServiceListingService.deleteService hard-deletes a listing, and a request must stay readable by
-- both parties afterward, so the service's title/price/currency are snapshotted onto the request
-- at creation time rather than only ever being read live off the (possibly now-gone) service.
CREATE TABLE service_requests (
    id                          UUID PRIMARY KEY,
    service_id                  UUID REFERENCES services(id) ON DELETE SET NULL,
    requester_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    service_title_snapshot      VARCHAR(255) NOT NULL,
    service_price_snapshot      INT,
    service_currency_snapshot   VARCHAR(10),
    description                 TEXT NOT NULL,
    requested_delivery_date     DATE,
    proposed_budget             INT,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_requests_requester_id ON service_requests (requester_id);
CREATE INDEX idx_service_requests_provider_id ON service_requests (provider_id);
CREATE INDEX idx_service_requests_service_id ON service_requests (service_id);

-- Belt-and-suspenders against races: ServiceRequestService.createRequest does the friendly
-- duplicate check first (returning a 409 with a message), this index catches anything that slips
-- through concurrently. Multiple NULL service_id rows (from a since-deleted service) don't
-- conflict with each other or with anything else — Postgres treats NULLs as distinct in a unique
-- index.
CREATE UNIQUE INDEX uq_service_requests_pending_per_service
    ON service_requests (service_id, requester_id) WHERE status = 'PENDING';

-- Mirrors service_links exactly (see V8) — each feature owns its own link rows rather than
-- sharing a table across modules.
CREATE TABLE service_request_links (
    service_request_id  UUID NOT NULL REFERENCES service_requests(id) ON DELETE CASCADE,
    link_order           INT NOT NULL,
    label                VARCHAR(100) NOT NULL,
    url                  VARCHAR(500) NOT NULL,
    PRIMARY KEY (service_request_id, link_order)
);
