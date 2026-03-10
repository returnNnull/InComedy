ALTER TABLE users
    ALTER COLUMN telegram_id DROP NOT NULL;

ALTER TABLE users
    ALTER COLUMN first_name DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS city TEXT;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active_role TEXT;

UPDATE users
SET display_name = COALESCE(
    NULLIF(display_name, ''),
    NULLIF(TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')), ''),
    COALESCE(username, 'InComedy user')
)
WHERE display_name = '';

CREATE TABLE IF NOT EXISTS auth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    username TEXT,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_identities_user_id
    ON auth_identities (user_id);

INSERT INTO auth_identities (
    id,
    user_id,
    provider,
    provider_user_id,
    username,
    linked_at,
    last_login_at
)
SELECT
    id,
    id,
    'telegram',
    telegram_id::TEXT,
    username,
    created_at,
    updated_at
FROM users u
WHERE telegram_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM auth_identities ai
      WHERE ai.provider = 'telegram'
        AND ai.provider_user_id = u.telegram_id::TEXT
  );

CREATE TABLE IF NOT EXISTS user_role_assignments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    scope_type TEXT NOT NULL DEFAULT 'global',
    scope_id TEXT NOT NULL DEFAULT 'global',
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, role, scope_type, scope_id)
);

CREATE INDEX IF NOT EXISTS idx_user_role_assignments_user_id
    ON user_role_assignments (user_id);

INSERT INTO user_role_assignments (
    id,
    user_id,
    role,
    scope_type,
    scope_id,
    status,
    created_at,
    updated_at
)
SELECT
    id,
    id,
    'audience',
    'global',
    'global',
    'active',
    created_at,
    updated_at
FROM users u
WHERE EXISTS (
    SELECT 1
    FROM auth_identities ai
    WHERE ai.user_id = u.id
)
  AND NOT EXISTS (
      SELECT 1
      FROM user_role_assignments ura
      WHERE ura.user_id = u.id
        AND ura.role = 'audience'
        AND ura.scope_type = 'global'
        AND ura.scope_id = 'global'
  );

UPDATE users
SET active_role = COALESCE(active_role, 'audience')
WHERE active_role IS NULL
  AND EXISTS (
      SELECT 1
      FROM auth_identities ai
      WHERE ai.user_id = users.id
  );

CREATE TABLE IF NOT EXISTS organizer_workspaces (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workspace_members (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES organizer_workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_role TEXT NOT NULL,
    invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    joined_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, user_id)
);
