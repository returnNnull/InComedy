package com.bam.incomedy.server.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

object DatabaseMigrationRunner {
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .baselineDescription("legacy schema bootstrap")
            .load()
            .migrate()
    }
}
