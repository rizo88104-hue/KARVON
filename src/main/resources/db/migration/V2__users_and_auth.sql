-- Users, their roles and delivery addresses; refresh tokens for login sessions.
CREATE TABLE users
(
    id               UUID PRIMARY KEY,
    phone            VARCHAR(13)  NOT NULL,
    full_name        VARCHAR(100),
    language         VARCHAR(2)   NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    telegram_chat_id BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL,
    CONSTRAINT ux_users_phone UNIQUE (phone)
);

CREATE TABLE user_roles
(
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(16) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE addresses
(
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label      VARCHAR(50),
    region     VARCHAR(100) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    district   VARCHAR(100),
    street     VARCHAR(255) NOT NULL,
    house      VARCHAR(50)  NOT NULL,
    apartment  VARCHAR(50),
    landmark   VARCHAR(255),
    phone      VARCHAR(13),
    is_default BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL
);

CREATE INDEX ix_addresses_user ON addresses (user_id);
-- At most one default address per user.
CREATE UNIQUE INDEX ux_addresses_default ON addresses (user_id) WHERE is_default;

CREATE TABLE refresh_tokens
(
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the token (hex); the token itself is never stored.
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
