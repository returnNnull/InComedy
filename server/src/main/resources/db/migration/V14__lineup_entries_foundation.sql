CREATE TABLE lineup_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    comedian_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID REFERENCES comedian_applications(id) ON DELETE SET NULL,
    order_index INTEGER NOT NULL,
    status TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (event_id, order_index),
    UNIQUE (event_id, application_id)
);

CREATE INDEX idx_lineup_entries_event_order
    ON lineup_entries (event_id, order_index);

CREATE INDEX idx_lineup_entries_comedian
    ON lineup_entries (comedian_user_id, created_at);
