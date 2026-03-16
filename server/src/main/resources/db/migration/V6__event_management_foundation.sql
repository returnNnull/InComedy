CREATE TABLE organizer_events (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES organizer_workspaces(id) ON DELETE CASCADE,
    venue_id UUID NOT NULL REFERENCES organizer_venues(id),
    venue_name TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    doors_open_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    status TEXT NOT NULL,
    sales_status TEXT NOT NULL,
    currency TEXT NOT NULL,
    visibility TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizer_events_workspace_id
    ON organizer_events (workspace_id);

CREATE INDEX idx_organizer_events_venue_id
    ON organizer_events (venue_id);

CREATE INDEX idx_organizer_events_status
    ON organizer_events (status);

CREATE TABLE event_hall_snapshots (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES organizer_events(id) ON DELETE CASCADE,
    source_template_id UUID NOT NULL REFERENCES hall_templates(id),
    source_template_name TEXT NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_hall_snapshots_event_id
    ON event_hall_snapshots (event_id);
