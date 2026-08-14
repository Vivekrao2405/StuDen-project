-- Backs the reuse-grace-period fix in RefreshTokenService.rotate(): lets a benign race between
-- two concurrent requests presenting the same still-valid refresh cookie (e.g. two open tabs
-- whose access tokens expire around the same moment, each independently calling
-- POST /auth/refresh) ride forward onto the already-rotated replacement instead of being treated
-- as token theft and logged out.
ALTER TABLE refresh_tokens ADD COLUMN replaced_by_hash VARCHAR(255);
