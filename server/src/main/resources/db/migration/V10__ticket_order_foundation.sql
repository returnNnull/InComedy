CREATE TABLE ticket_orders (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    currency TEXT NOT NULL,
    total_minor INTEGER NOT NULL,
    checkout_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_orders_event_id
    ON ticket_orders (event_id, status, checkout_expires_at);

CREATE INDEX idx_ticket_orders_user_id
    ON ticket_orders (user_id, status, checkout_expires_at);

CREATE TABLE ticket_order_lines (
    order_id UUID NOT NULL REFERENCES ticket_orders(id) ON DELETE CASCADE,
    inventory_unit_id UUID NOT NULL REFERENCES ticket_inventory_units(id) ON DELETE CASCADE,
    inventory_ref TEXT NOT NULL,
    label TEXT NOT NULL,
    price_minor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (order_id, inventory_unit_id)
);

CREATE INDEX idx_ticket_order_lines_order_id
    ON ticket_order_lines (order_id);

CREATE INDEX idx_ticket_order_lines_inventory_unit_id
    ON ticket_order_lines (inventory_unit_id);
