CREATE TABLE comedian_applications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    comedian_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    note TEXT,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (event_id, comedian_user_id)
);

CREATE INDEX idx_comedian_applications_event_status
    ON comedian_applications (event_id, status, created_at);

CREATE INDEX idx_comedian_applications_comedian
    ON comedian_applications (comedian_user_id, created_at);
