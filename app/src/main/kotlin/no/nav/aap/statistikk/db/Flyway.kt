package no.nav.aap.statistikk.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

class Flyway(config: DbConfig) {
    private val dataSource = HikariDataSource().apply {
        jdbcUrl = config.jdbcUrl
        username = config.userName
        password = config.password
        connectionTestQuery = "SELECT 1"
    }

    private val flyway = Flyway
        .configure()
        .cleanDisabled(true)
        .dataSource(dataSource)
        .locations("migrering")
        .validateMigrationNaming(true)
        .load()

    fun createAndMigrateDataSource(): HikariDataSource {
        flyway.migrate()

        return dataSource  // Returnerer den konfigurerte og migrerte datasourcen
    }

    fun clean() {
        flyway.clean()
    }
}