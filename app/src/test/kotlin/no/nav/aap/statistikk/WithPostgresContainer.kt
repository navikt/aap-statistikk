package no.nav.aap.statistikk

import com.zaxxer.hikari.HikariDataSource
import no.nav.aap.statistikk.db.DbConfig
import no.nav.aap.statistikk.db.Flyway
import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.sql.DataSource


@Target(
    AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.CLASS,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(
    Postgres.WithPostgresContainer::class
)
annotation class Postgres {
    class WithPostgresContainer : AfterEachCallback, BeforeEachCallback, ParameterResolver {

        companion object {
            private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15").apply {
                waitingFor(HostPortWaitStrategy().withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
            }
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

        override fun beforeEach(context: ExtensionContext?) {
            flyway.createAndMigrateDataSource()
        }

        override fun afterEach(context: ExtensionContext?) {
            flyway.clean()
        }

        override fun supportsParameter(
            parameterContext: ParameterContext?,
            extensionContext: ExtensionContext?
        ): Boolean {
            return parameterContext?.isAnnotated(Postgres::class.java) == true && (parameterContext.parameter.type == DataSource::class.java)
        }

        override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any {
            return dataSource
        }
    }
}
