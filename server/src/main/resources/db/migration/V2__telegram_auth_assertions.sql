CREATE TABLE IF NOT EXISTS telegram_auth_assertions (
    hash TEXT PRIMARY KEY,
    telegram_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_telegram_auth_assertions_expires_at
    ON telegram_auth_assertions (expires_at);
