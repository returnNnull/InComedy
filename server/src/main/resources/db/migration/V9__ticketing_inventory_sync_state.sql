CREATE TABLE ticket_inventory_sync_state (
    event_id UUID PRIMARY KEY REFERENCES organizer_events(id) ON DELETE CASCADE,
    source_event_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reconciled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
