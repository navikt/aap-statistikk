package no.nav.aap.statistikk.testutils

import no.nav.aap.komponenter.dbmigrering.Migrering
import no.nav.aap.statistikk.AppConfig
import no.nav.aap.statistikk.db.DbConfig
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
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
        private val postgresContainer = PostgreSQLContainer("postgres:18").apply {
            waitingFor(
                HostPortWaitStrategy().withStartupTimeout(
                    Duration.of(
                        60L,
                        ChronoUnit.SECONDS
                    )
                )
            )
        }
        private var dataSource: DataSource
        private val dbConfig: DbConfig
        private var truncateStatement: String

        init {
            postgresContainer.start()
            dbConfig = DbConfig(
                jdbcUrl = postgresContainer.jdbcUrl,
                userName = postgresContainer.username,
                password = postgresContainer.password,
                poolSize = AppConfig.hikariMaxPoolSize
            )
            dataSource = dbConfig.datasource().also {
                Migrering.migrate(it)
            }
            truncateStatement = buildTruncateStatement()
        }

        private fun buildTruncateStatement(): String {
            dataSource.connection.use { conn ->
                val result = conn.metaData.getTables(null, "public", null, arrayOf("TABLE"))
                val tables = buildList {
                    while (result.next()) {
                        val name = result.getString("TABLE_NAME")
                        if (name != "flyway_schema_history" && !name.startsWith("kodeverk_")) {
                            add("\"$name\"")
                        }
                    }
                }
                return "TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE"
            }
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.execute(truncateStatement) }
        }
    }

    override fun afterEach(context: ExtensionContext) {
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