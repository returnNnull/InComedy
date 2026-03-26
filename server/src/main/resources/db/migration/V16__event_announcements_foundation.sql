CREATE TABLE event_announcements (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES organizer_events(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    author_role TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CHECK (author_role IN ('organizer', 'host', 'system')),
    CHECK (char_length(trim(message)) > 0),
    CHECK (char_length(message) <= 1000)
);

CREATE INDEX idx_event_announcements_event_id_created_at
    ON event_announcements(event_id, created_at);
