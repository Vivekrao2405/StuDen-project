-- Admin Communications Center (Email + Push + In-App via Resend). User.email (V1) remains the
-- sole email field -- nothing here adds a second one. filter_json columns hold a small JSON tree
-- (AND/OR groups of typed conditions, see com.studen.communication.audience) compiled server-side
-- into a JPA Specification<User> -- admins never see or write SQL.

ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD COLUMN marketing_opt_out BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE communication_templates (
    id               UUID PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    category         VARCHAR(40)  NOT NULL,
    email_subject    VARCHAR(500),
    email_body_html  TEXT,
    push_title       VARCHAR(255),
    push_body        VARCHAR(1000),
    inapp_title      VARCHAR(255),
    inapp_body       VARCHAR(1000),
    cta_text         VARCHAR(100),
    cta_url          VARCHAR(500),
    archived         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by       UUID         NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Stores the filter DEFINITION only -- never a resolved recipient list. Re-resolved live by
-- AudienceService every time the segment is previewed or used, so its count/membership always
-- reflects current data (spec: "No Portfolio" shows a different count next week automatically).
CREATE TABLE communication_segments (
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(1000),
    filter_json  TEXT         NOT NULL,
    created_by   UUID         NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE communication_campaigns (
    id                        UUID PRIMARY KEY,
    name                      VARCHAR(255) NOT NULL,
    category                  VARCHAR(40)  NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    is_marketing              BOOLEAN      NOT NULL DEFAULT FALSE,
    filter_json               TEXT         NOT NULL,
    template_id               UUID         REFERENCES communication_templates(id),
    segment_id                UUID         REFERENCES communication_segments(id),
    send_email                BOOLEAN      NOT NULL DEFAULT FALSE,
    send_push                 BOOLEAN      NOT NULL DEFAULT FALSE,
    send_inapp                BOOLEAN      NOT NULL DEFAULT FALSE,
    email_subject             VARCHAR(500),
    email_body_html           TEXT,
    push_title                VARCHAR(255),
    push_body                 VARCHAR(1000),
    inapp_title               VARCHAR(255),
    inapp_body                VARCHAR(1000),
    cta_text                  VARCHAR(100),
    cta_url                   VARCHAR(500),
    -- Snapshot frozen the moment recipients are resolved (send-now or scheduler pickup) -- a
    -- later template/segment edit must never rewrite this campaign's own history.
    resolved_recipient_count  INT,
    scheduled_at              TIMESTAMPTZ,
    processing_started_at     TIMESTAMPTZ,
    sent_at                   TIMESTAMPTZ,
    created_by                UUID         NOT NULL REFERENCES users(id),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_communication_campaigns_status ON communication_campaigns (status);
CREATE INDEX idx_communication_campaigns_scheduled_at ON communication_campaigns (scheduled_at) WHERE status = 'SCHEDULED';

-- One row per (campaign, user, channel) -- the unique constraint is the whole duplicate-prevention
-- mechanism: no matter how overlapping AND/OR filter branches, repeated scheduler ticks, or a
-- retry produce a candidate send, a second insert for the same triple is a no-op
-- (ON CONFLICT DO NOTHING in CampaignSendService), so a student can never receive the same
-- campaign twice on the same channel except via an explicit retry re-queueing an existing FAILED
-- row in place.
CREATE TABLE communication_recipients (
    id                    UUID PRIMARY KEY,
    campaign_id           UUID        NOT NULL REFERENCES communication_campaigns(id) ON DELETE CASCADE,
    user_id               UUID        NOT NULL REFERENCES users(id),
    channel               VARCHAR(10) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    -- Snapshot of User.email at resolve time, for audit/history display only -- never re-read as
    -- a send source; EmailService always sends to the live User.email at dispatch time.
    recipient_email       VARCHAR(255),
    provider_message_id   VARCHAR(255),
    error_message         TEXT,
    delivered_at          TIMESTAMPTZ,
    opened_at             TIMESTAMPTZ,
    clicked_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_communication_recipient UNIQUE (campaign_id, user_id, channel)
);

CREATE INDEX idx_communication_recipients_campaign_status ON communication_recipients (campaign_id, status);
CREATE INDEX idx_communication_recipients_provider_message_id ON communication_recipients (provider_message_id);
