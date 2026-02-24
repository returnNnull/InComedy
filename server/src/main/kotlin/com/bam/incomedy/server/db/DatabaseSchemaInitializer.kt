package com.bam.incomedy.server.db

import javax.sql.DataSource

object DatabaseSchemaInitializer {
    fun ensure(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id UUID PRIMARY KEY,
                        telegram_id BIGINT NOT NULL UNIQUE,
                        first_name TEXT NOT NULL,
                        last_name TEXT,
                        username TEXT,
                        photo_url TEXT,
                        session_revoked_at TIMESTAMPTZ,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    );
                    """.trimIndent(),
                )

                statement.execute(
                    """
                    ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS session_revoked_at TIMESTAMPTZ;
                    """.trimIndent(),
                )

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS refresh_tokens (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        token_hash TEXT NOT NULL UNIQUE,
                        expires_at TIMESTAMPTZ NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    );
                    """.trimIndent(),
                )
            }
        }
    }
}
