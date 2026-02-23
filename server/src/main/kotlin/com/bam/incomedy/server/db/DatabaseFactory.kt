package com.bam.incomedy.server.db

import com.bam.incomedy.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object DatabaseFactory {
    fun create(config: DatabaseConfig): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 1
            isAutoCommit = true
        }
        return HikariDataSource(hikariConfig)
    }
}

