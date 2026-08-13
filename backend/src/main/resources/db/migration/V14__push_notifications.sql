-- Real Web Push + in-app notification persistence. Bundles all three tables for this one
-- cohesive feature into a single migration (same precedent as V9's service_requests +
-- service_request_links) since none of them has independent deploy value on its own.

-- Endpoint is globally unique (not per-user): a browser push subscription endpoint is
-- inherently unique per (browser install, origin). Re-subscribing the same browser (permission
-- reset, storage cleared) or a different user logging into the same shared/kiosk device must
-- reassign this row via upsert-by-endpoint rather than create a duplicate that both point at the
-- same push channel — see PushSubscriptionService.
CREATE TABLE push_subscriptions (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    user_agent  VARCHAR(255),
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_push_subscriptions_endpoint ON push_subscriptions (endpoint);
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions (user_id);

-- In-app notification feed backing the /notifications page and bell badge. `url` is the
-- precomputed deep link (built once, server-side, from type+resourceId — see
-- NotificationUrlBuilder) so the frontend and the service worker's push payload both just
-- consume a ready string instead of re-deriving it from type+resourceId in two places.
CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(32) NOT NULL,
    message      TEXT NOT NULL,
    resource_id  UUID,
    url          VARCHAR(255) NOT NULL,
    read         BOOLEAN NOT NULL DEFAULT false,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at DESC);
-- Partial index scoped to only unread rows — the unread-count badge's access path, kept small
-- since most notifications end up read and don't need to be indexed here.
CREATE INDEX idx_notifications_user_id_unread ON notifications (user_id) WHERE read = false;

-- One row per (user, type) rather than a fixed set of boolean columns on `users`, so adding an
-- 8th notification type later needs zero schema change — a missing row simply means "not yet
-- customized," resolved to true (enabled) by the application, not "opted out." push_enabled and
-- in_app_enabled are independent: a user disabling push for a category almost certainly still
-- wants it in their in-app feed.
CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(32) NOT NULL,
    push_enabled    BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled  BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notification_preferences_user_id_type ON notification_preferences (user_id, type);
