package no.nav.aap.statistikk.testutils

import com.zaxxer.hikari.HikariDataSource
import no.nav.aap.statistikk.AppConfig
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Migrering
import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.sql.DataSource


@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CLASS,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(
    WithPostgresContainer::class
)
annotation class Postgres

class WithPostgresContainer : AfterEachCallback, BeforeEachCallback, ParameterResolver {

    companion object {
        private val postgresContainer = PostgreSQLContainer("postgres:16").apply {
            waitingFor(
                HostPortWaitStrategy().withStartupTimeout(
                    Duration.of(
                        60L,
                        ChronoUnit.SECONDS
                    )
                )
            )
        }
        private var dataSource: HikariDataSource
        private val flyway: Migrering
        private val dbConfig: DbConfig

        init {
            System.setProperty("flyway.cleanDisabled", false.toString())
            postgresContainer.start()
            dbConfig = DbConfig(
                jdbcUrl = postgresContainer.jdbcUrl,
                userName = postgresContainer.username,
                password = postgresContainer.password,
                poolSize = AppConfig.hikariMaxPoolSize
            )
            flyway = Migrering(dbConfig)
            dataSource = flyway.createAndMigrateDataSource()
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        flyway.clean()
        flyway.createAndMigrateDataSource()
    }

    override fun afterEach(context: ExtensionContext) {
//            flyway.clean()
//            flyway.createAndMigrateDataSource()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return (parameterContext.isAnnotated(Postgres::class.java) && (parameterContext.parameter.type == DataSource::class.java)) || (parameterContext.isAnnotated(
            Postgres::class.java
        ) && (parameterContext.parameter.type == DbConfig::class.java))
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        if (parameterContext.parameter.type == DataSource::class.java) {
            return dataSource
        }
        if (parameterContext.parameter.type == DbConfig::class.java) {
            return dbConfig
        }
        throw IllegalArgumentException("Not supported parameter type")
    }
}