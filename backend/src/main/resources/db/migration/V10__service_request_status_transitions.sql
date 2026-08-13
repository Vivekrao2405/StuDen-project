-- Phase 6.6: PENDING -> ACCEPTED/REJECTED transitions on service_requests (Phase 6.5, V9).
-- Nullable, no defaults — populated only when the corresponding transition actually happens.
ALTER TABLE service_requests
    ADD COLUMN accepted_at     TIMESTAMPTZ,
    ADD COLUMN rejected_at     TIMESTAMPTZ,
    ADD COLUMN rejection_reason VARCHAR(500);
