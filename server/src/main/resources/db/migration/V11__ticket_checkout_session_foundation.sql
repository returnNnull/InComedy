CREATE TABLE ticket_checkout_sessions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES ticket_orders(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    status TEXT NOT NULL,
    provider_payment_id TEXT NOT NULL UNIQUE,
    provider_status TEXT NOT NULL,
    confirmation_url TEXT NOT NULL,
    return_url TEXT NOT NULL,
    checkout_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_checkout_sessions_provider_status
    ON ticket_checkout_sessions (provider, status, checkout_expires_at);
