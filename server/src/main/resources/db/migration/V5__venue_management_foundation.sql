CREATE TABLE organizer_venues (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES organizer_workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    city TEXT NOT NULL,
    address TEXT NOT NULL,
    timezone TEXT NOT NULL,
    capacity INTEGER NOT NULL,
    description TEXT,
    contacts_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_organizer_venues_workspace_id
    ON organizer_venues (workspace_id);

CREATE TABLE hall_templates (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL REFERENCES organizer_venues(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    version INTEGER NOT NULL,
    status TEXT NOT NULL,
    layout_json TEXT NOT NULL,
    cloned_from_template_id UUID REFERENCES hall_templates(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hall_templates_venue_id
    ON hall_templates (venue_id);

CREATE INDEX idx_hall_templates_status
    ON hall_templates (status);
