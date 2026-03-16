package com.bam.incomedy.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseMigrationRunnerTest {

    @Test
    fun `migrate creates current schema on clean database`() {
        postgresDataSource().use { dataSource ->
            DatabaseMigrationRunner.migrate(dataSource)

            dataSource.connection.use { connection ->
                assertTrue(tableExists(connection, "users"))
                assertTrue(tableExists(connection, "refresh_tokens"))
                assertTrue(tableExists(connection, "telegram_auth_assertions"))
                assertTrue(tableExists(connection, "auth_identities"))
                assertTrue(tableExists(connection, "credential_accounts"))
                assertTrue(tableExists(connection, "user_role_assignments"))
                assertTrue(tableExists(connection, "organizer_workspaces"))
                assertTrue(tableExists(connection, "workspace_members"))
                assertTrue(tableExists(connection, "organizer_venues"))
                assertTrue(tableExists(connection, "hall_templates"))
                assertEquals(5, appliedMigrationCount(connection))
            }
        }
    }

    @Test
    fun `migrate upgrades legacy schema without losing existing user data`() {
        postgresDataSource().use { dataSource ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        CREATE TABLE users (
                            id UUID PRIMARY KEY,
                            telegram_id BIGINT NOT NULL UNIQUE,
                            first_name TEXT NOT NULL,
                            last_name TEXT,
                            username TEXT,
                            photo_url TEXT,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                        );
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        CREATE TABLE refresh_tokens (
                            id UUID PRIMARY KEY,
                            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            token_hash TEXT NOT NULL UNIQUE,
                            expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                        );
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        INSERT INTO users (
                            id,
                            telegram_id,
                            first_name,
                            last_name,
                            username,
                            photo_url,
                            created_at,
                            updated_at
                        ) VALUES (
                            '00000000-0000-0000-0000-000000000111',
                            777001,
                            'Legacy',
                            'User',
                            'legacy_user',
                            'https://t.me/i/userpic/320/legacy_user.jpg',
                            NOW(),
                            NOW()
                        );
                        """.trimIndent(),
                    )
                }
            }

            DatabaseMigrationRunner.migrate(dataSource)

            dataSource.connection.use { connection ->
                assertTrue(tableExists(connection, "flyway_schema_history"))
                assertTrue(tableExists(connection, "auth_identities"))
                assertTrue(tableExists(connection, "user_role_assignments"))
                assertEquals("Legacy User", userDisplayName(connection))
                assertEquals("telegram", linkedProvider(connection))
                assertEquals("audience", assignedRole(connection))
                assertEquals("audience", activeRole(connection))
            }
        }
    }

    private fun postgresDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 2
            minimumIdle = 1
        }
        return HikariDataSource(config)
    }

    private fun tableExists(connection: java.sql.Connection, tableName: String): Boolean {
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE upper(table_schema) = 'PUBLIC' AND upper(table_name) = upper(?)
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { result ->
                result.next()
                return result.getBoolean(1)
            }
        }
    }

    private fun appliedMigrationCount(connection: java.sql.Connection): Int {
        connection.prepareStatement(
            """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE success = TRUE AND version IS NOT NULL
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                return result.getInt(1)
            }
        }
    }

    private fun userDisplayName(connection: java.sql.Connection): String {
        connection.prepareStatement(
            """
            SELECT display_name
            FROM users
            WHERE id = '00000000-0000-0000-0000-000000000111'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                return result.getString(1)
            }
        }
    }

    private fun linkedProvider(connection: java.sql.Connection): String {
        connection.prepareStatement(
            """
            SELECT provider
            FROM auth_identities
            WHERE user_id = '00000000-0000-0000-0000-000000000111'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                return result.getString(1)
            }
        }
    }

    private fun assignedRole(connection: java.sql.Connection): String {
        connection.prepareStatement(
            """
            SELECT role
            FROM user_role_assignments
            WHERE user_id = '00000000-0000-0000-0000-000000000111'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                return result.getString(1)
            }
        }
    }

    private fun activeRole(connection: java.sql.Connection): String {
        connection.prepareStatement(
            """
            SELECT active_role
            FROM users
            WHERE id = '00000000-0000-0000-0000-000000000111'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                return result.getString(1)
            }
        }
    }
}
