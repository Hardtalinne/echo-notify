CREATE TABLE IF NOT EXISTS notification_outbox (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    topic VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_status_next_attempt
    ON notification_outbox(status, next_attempt_at);
