-- In-app notifications (shown on the site): channel IN_APP, used when neither Telegram nor SMS can reach the user.
-- They are saved as SENT; read_at is set when the user reads them (null = unread).
ALTER TABLE notifications ADD COLUMN read_at TIMESTAMPTZ;

-- The bell: unread count and the newest first of one user.
CREATE INDEX ix_notifications_in_app ON notifications (user_id, created_at DESC) WHERE channel = 'IN_APP';
