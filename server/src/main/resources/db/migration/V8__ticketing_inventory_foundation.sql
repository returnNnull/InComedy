CREATE TABLE ticket_inventory_units (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    inventory_ref TEXT NOT NULL,
    inventory_type TEXT NOT NULL,
    snapshot_target_type TEXT NOT NULL,
    snapshot_target_ref TEXT NOT NULL,
    label TEXT NOT NULL,
    price_zone_id TEXT,
    price_zone_name TEXT,
    price_minor INTEGER,
    currency TEXT NOT NULL,
    base_status TEXT NOT NULL,
    status TEXT NOT NULL,
    active_hold_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ticket_inventory_units_event_ref
        UNIQUE (event_id, inventory_ref)
);

CREATE INDEX idx_ticket_inventory_units_event_id
    ON ticket_inventory_units (event_id, status);

CREATE INDEX idx_ticket_inventory_units_snapshot_target
    ON ticket_inventory_units (event_id, snapshot_target_type, snapshot_target_ref);

CREATE TABLE seat_holds (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    inventory_unit_id UUID NOT NULL REFERENCES ticket_inventory_units(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_seat_holds_event_id
    ON seat_holds (event_id, status, expires_at);

CREATE INDEX idx_seat_holds_inventory_unit_id
    ON seat_holds (inventory_unit_id, status);

CREATE INDEX idx_seat_holds_user_id
    ON seat_holds (user_id, status);

ALTER TABLE ticket_inventory_units
    ADD CONSTRAINT fk_ticket_inventory_units_active_hold
        FOREIGN KEY (active_hold_id)
        REFERENCES seat_holds(id)
        ON DELETE SET NULL;
