-- Proves that Flyway migrations run. Real tables come with the business modules.
CREATE TABLE app_info
(
    name       VARCHAR(100) PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_info (name, value)
VALUES ('schema', 'v1');
