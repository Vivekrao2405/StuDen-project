-- Phase 6.8: post-acceptance work lifecycle for an ACCEPTED service_request.
CREATE TABLE work_orders (
    id                      UUID PRIMARY KEY,
    service_request_id     UUID NOT NULL UNIQUE REFERENCES service_requests(id) ON DELETE CASCADE,
    provider_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requester_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    submitted_at            TIMESTAMPTZ,
    submission_description  TEXT,
    submission_link         VARCHAR(500),
    completed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancellation_reason     VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- UNIQUE on service_request_id is the DB-level guarantee behind "at most one order per request"
-- (see OrderService.getOrCreateOrder), same pattern as conversations.service_request_id in V11.
-- No title/price/delivery-date/description columns here — WorkOrder reads all of that through its
-- serviceRequest association, which already froze those fields at request-creation time.

CREATE INDEX idx_work_orders_requester_id ON work_orders (requester_id);
CREATE INDEX idx_work_orders_provider_id ON work_orders (provider_id);
