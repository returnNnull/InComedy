CREATE TABLE event_price_zones (
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    id TEXT NOT NULL,
    source_template_price_zone_id TEXT,
    name TEXT NOT NULL,
    price_minor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    sales_start_at TIMESTAMP WITH TIME ZONE,
    sales_end_at TIMESTAMP WITH TIME ZONE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, id)
);

CREATE INDEX idx_event_price_zones_event_id
    ON event_price_zones (event_id, sort_order);

CREATE TABLE event_pricing_assignments (
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_ref TEXT NOT NULL,
    event_price_zone_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, target_type, target_ref),
    CONSTRAINT fk_event_pricing_assignments_price_zone
        FOREIGN KEY (event_id, event_price_zone_id)
        REFERENCES event_price_zones(event_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_event_pricing_assignments_event_id
    ON event_pricing_assignments (event_id);

CREATE TABLE event_availability_overrides (
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL,
    target_ref TEXT NOT NULL,
    availability_status TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, target_type, target_ref)
);

CREATE INDEX idx_event_availability_overrides_event_id
    ON event_availability_overrides (event_id);
