CREATE TABLE comedian_payout_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    payout_provider TEXT NOT NULL,
    legal_type TEXT NOT NULL,
    beneficiary_ref TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comedian_payout_profiles_status
    ON comedian_payout_profiles (verification_status, updated_at DESC);

CREATE TABLE donation_intents (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    comedian_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    donor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
    currency TEXT NOT NULL,
    message TEXT,
    status TEXT NOT NULL,
    payment_id TEXT,
    idempotency_key TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (donor_user_id, idempotency_key)
);

CREATE INDEX idx_donation_intents_donor
    ON donation_intents (donor_user_id, created_at DESC);

CREATE INDEX idx_donation_intents_comedian
    ON donation_intents (comedian_user_id, created_at DESC);

CREATE INDEX idx_donation_intents_event
    ON donation_intents (event_id, created_at DESC);
