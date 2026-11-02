-- Outbox of customer notifications. A row is written after the business transaction commits and sent
-- asynchronously; FAILED rows are retried by a scheduled job with a growing delay.
CREATE TABLE notifications
(
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id),
    order_id        UUID,
    -- SMS or TELEGRAM
    channel         VARCHAR(10) NOT NULL,
    -- key in messages_uz / messages_ru, e.g. order.paid
    template_key    VARCHAR(60) NOT NULL,
    -- JSON array of the template parameters (strings)
    params          TEXT        NOT NULL,
    -- PENDING, SENT or FAILED
    status          VARCHAR(10) NOT NULL,
    attempts        INT         NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    sent_at         TIMESTAMPTZ,
    -- when the row may be sent next (also a short lease while it is being sent)
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_notifications_status_next ON notifications (status, next_attempt_at);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at);

-- Channels the customer allows. No row = both allowed.
CREATE TABLE notification_settings
(
    user_id    UUID PRIMARY KEY REFERENCES users (id),
    sms        BOOLEAN     NOT NULL,
    telegram   BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_users_telegram_chat ON users (telegram_chat_id);
