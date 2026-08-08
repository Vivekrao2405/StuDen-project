CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    full_name          VARCHAR(255)  NOT NULL,
    email              VARCHAR(255)  NOT NULL,
    phone              VARCHAR(20),
    password_hash      VARCHAR(255)  NOT NULL,
    role               VARCHAR(20)   NOT NULL DEFAULT 'STUDENT',
    profile_image_url  VARCHAR(500),
    university         VARCHAR(255),
    is_verified        BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_email ON users (email);
CREATE UNIQUE INDEX uk_users_phone ON users (phone);
