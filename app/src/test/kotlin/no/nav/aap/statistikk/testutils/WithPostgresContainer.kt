package no.nav.aap.statistikk.testutils

import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Flyway
import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
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
    Postgres.WithPostgresContainer::class
)
annotation class Postgres {
    class WithPostgresContainer : AfterEachCallback, BeforeEachCallback, ParameterResolver {

        companion object {
            private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:16").apply {
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
            private val flyway: Flyway
            private val dbConfig: DbConfig

            init {
                System.setProperty("flyway.cleanDisabled", false.toString())
                postgresContainer.start()
                dbConfig = DbConfig(
                    jdbcUrl = postgresContainer.jdbcUrl,
                    userName = postgresContainer.username,
                    password = postgresContainer.password
                )
                flyway = Flyway(dbConfig, SimpleMeterRegistry())
                dataSource = flyway.createAndMigrateDataSource()
            }
        }

        override fun beforeEach(context: ExtensionContext?) {
            flyway.clean()
            flyway.createAndMigrateDataSource()
        }

        override fun afterEach(context: ExtensionContext?) {
            flyway.clean()
            flyway.createAndMigrateDataSource()
        }

        override fun supportsParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Boolean {
            return (parameterContext?.isAnnotated(Postgres::class.java) == true && (parameterContext.parameter.type == DataSource::class.java))
                    || ((parameterContext?.isAnnotated(Postgres::class.java) == true && (parameterContext.parameter.type == DbConfig::class.java)))
        }

        override fun resolveParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Any {
            if (parameterContext == null) {
                throw IllegalArgumentException("ParameterContext cannot be null")
            }
            if (parameterContext.parameter.type == DataSource::class.java) {
                return dataSource
            }
            if (parameterContext.parameter.type == DbConfig::class.java) {
                return dbConfig
            }
            throw IllegalArgumentException("Not supported parameter type")
        }
    }
}
