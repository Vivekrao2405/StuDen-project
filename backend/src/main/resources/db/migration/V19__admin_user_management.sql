-- Admin User Management (Phase: Admin). `is_active` already exists (V1) and already gates
-- login, refresh-token rotation, and public profile/project/service visibility — deactivation
-- reuses it as-is. `deleted_at` distinguishes a permanently-deleted (anonymized tombstone)
-- account from a merely deactivated one without adding a duplicate status column: ACTIVE =
-- is_active true; DEACTIVATED = is_active false, deleted_at null; DELETED = is_active false,
-- deleted_at not null.
--
-- Permanent delete never removes the users row itself (see AdminUserService) — several other
-- tables (conversations, messages, work_orders, service_requests) cascade-delete on their user
-- FK, so physically deleting a user would destroy the *other* party's conversation/order
-- history too. Anonymizing in place avoids that entirely and needs no FK changes anywhere else.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE admin_audit_log (
    id               UUID PRIMARY KEY,
    admin_user_id    UUID NOT NULL REFERENCES users(id),
    target_user_id   UUID NOT NULL REFERENCES users(id),
    action           VARCHAR(40) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_admin_user_id ON admin_audit_log (admin_user_id);
CREATE INDEX idx_admin_audit_log_target_user_id ON admin_audit_log (target_user_id);
