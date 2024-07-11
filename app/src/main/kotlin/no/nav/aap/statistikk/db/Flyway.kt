package no.nav.aap.statistikk.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Flyway {
    fun createAndMigrateDataSource(config: DbConfig): DataSource {
        val dataSource = HikariDataSource().apply {
            jdbcUrl = config.jdbcUrl
            username = config.userName
            password = config.password
        }

        val flyway = Flyway
            .configure()
            .cleanDisabled(false)
            .cleanOnValidationError(true) // TODO: endre denne når skjema har stabilisert seg litt
            .dataSource(dataSource)
            .locations("migrering")
            .validateMigrationNaming(true)
            .load()

        flyway.migrate()

        return dataSource  // Returnerer den konfigurerte og migrerte datasourcen
    }
}