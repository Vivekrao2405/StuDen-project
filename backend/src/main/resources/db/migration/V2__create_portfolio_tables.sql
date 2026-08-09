CREATE TABLE student_portfolios (
    id                   UUID PRIMARY KEY,
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    headline             VARCHAR(255) NOT NULL,
    bio                  TEXT,
    experience_summary   TEXT,
    hourly_rate          NUMERIC(10,2),
    response_time        VARCHAR(100),
    location             VARCHAR(255),
    is_available         BOOLEAN       NOT NULL DEFAULT TRUE,
    public_slug          VARCHAR(150)  NOT NULL,
    cover_image_url      VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_student_portfolios_user_id ON student_portfolios (user_id);
CREATE UNIQUE INDEX uk_student_portfolios_public_slug ON student_portfolios (public_slug);

CREATE TABLE education (
    id              UUID PRIMARY KEY,
    portfolio_id    UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    degree          VARCHAR(255) NOT NULL,
    field_of_study  VARCHAR(255),
    institution     VARCHAR(255) NOT NULL,
    start_year      INTEGER      NOT NULL,
    end_year        INTEGER,
    is_current      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_education_portfolio_id ON education (portfolio_id);

CREATE TABLE certificates (
    id               UUID PRIMARY KEY,
    portfolio_id     UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    issued_by        VARCHAR(255),
    issue_date       DATE,
    certificate_url  VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_certificates_portfolio_id ON certificates (portfolio_id);

CREATE TABLE profile_shares (
    id                UUID PRIMARY KEY,
    portfolio_id      UUID NOT NULL REFERENCES student_portfolios(id) ON DELETE CASCADE,
    share_title       VARCHAR(255),
    meta_description  VARCHAR(500),
    show_contact      BOOLEAN      NOT NULL DEFAULT TRUE,
    show_email        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_profile_shares_portfolio_id ON profile_shares (portfolio_id);

CREATE TABLE profile_cards (
    id            UUID PRIMARY KEY,
    share_id      UUID NOT NULL REFERENCES profile_shares(id) ON DELETE CASCADE,
    type          VARCHAR(10)  NOT NULL,
    file_url      VARCHAR(500) NOT NULL,
    generated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_profile_cards_share_id ON profile_cards (share_id);
