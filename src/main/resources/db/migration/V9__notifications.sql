-- What the customer wants to receive. SMS for important events is still sent when Telegram is linked.
ALTER TABLE users ADD COLUMN notify_sms BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN notify_telegram BOOLEAN NOT NULL DEFAULT TRUE;
-- One Telegram chat belongs to one user.
CREATE UNIQUE INDEX ux_users_telegram_chat ON users (telegram_chat_id) WHERE telegram_chat_id IS NOT NULL;

-- Outbox of customer notifications: one row per channel. The text is rendered from template_key and params
-- in the language the user had when the row was created. FAILED rows are retried with backoff
-- while next_attempt_at is set.
CREATE TABLE notifications
(
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    order_id        UUID,
    -- SMS | TELEGRAM
    channel         VARCHAR(10)  NOT NULL,
    -- phone (E.164) or Telegram chat id
    recipient       VARCHAR(32)  NOT NULL,
    language        VARCHAR(2)   NOT NULL,
    template_key    VARCHAR(64)  NOT NULL,
    -- JSON array of strings: the {0}, {1}... of the template
    params          TEXT         NOT NULL,
    -- PENDING | SENT | FAILED
    status          VARCHAR(10)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    next_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL
);

CREATE INDEX ix_notifications_status ON notifications (status, next_attempt_at);
CREATE INDEX ix_notifications_created ON notifications (created_at DESC);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
