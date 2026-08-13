-- Backs the access-token + refresh-token session architecture: a 15-minute JWT access token is no
-- longer terminal on expiry, because the frontend can silently exchange a still-valid refresh token
-- for a new one via POST /api/v1/auth/refresh. Only the SHA-256 hash of the raw refresh token is
-- stored (never the raw value) so a leaked database dump can't be replayed as a live session.
-- token_hash has no direct FK-style relation beyond user_id; rotation on every refresh (old row
-- revoked, new row inserted) means a user accumulates one row per login/device rather than a
-- single mutable session, which is what makes concurrent multi-device sessions and per-device
-- logout possible without extra modeling.
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    user_agent   VARCHAR(255),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
