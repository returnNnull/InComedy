CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES ticket_orders(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    inventory_unit_id UUID NOT NULL REFERENCES ticket_inventory_units(id) ON DELETE RESTRICT,
    inventory_ref TEXT NOT NULL,
    label TEXT NOT NULL,
    status TEXT NOT NULL,
    qr_payload TEXT NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    checked_in_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (order_id, inventory_unit_id)
);

CREATE INDEX idx_tickets_event_status
    ON tickets (event_id, status, issued_at);

CREATE INDEX idx_tickets_checked_in_by_user
    ON tickets (checked_in_by_user_id, checked_in_at);
