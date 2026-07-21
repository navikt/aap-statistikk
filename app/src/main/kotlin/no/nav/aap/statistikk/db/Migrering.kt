package no.nav.aap.statistikk.db

import com.zaxxer.hikari.HikariDataSource
import no.nav.aap.statistikk.PrometheusProvider
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Migrering(config: DbConfig) {
    private val dataSource = HikariDataSource().apply {
        jdbcUrl = config.jdbcUrl
        username = config.userName
        password = config.password
        connectionTestQuery = "SELECT 1"
        metricRegistry = PrometheusProvider.prometheus
    }

    private val flyway = Flyway
        .configure()
        .dataSource(dataSource)
        .locations("migrering")
        .validateMigrationNaming(true)
        .load()

    fun createAndMigrateDataSource(): DataSource {
        flyway.migrate()

        return dataSource  // Returnerer den konfigurerte og migrerte datasourcen
    }

}