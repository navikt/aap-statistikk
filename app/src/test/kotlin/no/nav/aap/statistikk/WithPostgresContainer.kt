package no.nav.aap.statistikk

import com.zaxxer.hikari.HikariDataSource
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer

abstract class WithPostgresContainer {

    companion object {
        private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15")
        private var dataSource: HikariDataSource
        private val flyway: Flyway
        private val dbConfig: DbConfig

        init {
            postgresContainer.start()
            dbConfig = DbConfig(
                jdbcUrl = postgresContainer.jdbcUrl,
                userName = postgresContainer.username,
                password = postgresContainer.password
            )
            flyway = Flyway(dbConfig)
            dataSource = flyway.createAndMigrateDataSource()
        }
    }


    @BeforeEach
    fun beforeEach() {
        flyway.createAndMigrateDataSource()
    }

    @AfterEach
    fun afterEach() {
        flyway.clean()
    }

    fun postgresDataSource(): HikariDataSource {
        return dataSource
    }
}