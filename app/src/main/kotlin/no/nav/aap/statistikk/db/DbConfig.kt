package no.nav.aap.statistikk.db

import com.zaxxer.hikari.HikariDataSource
import no.nav.aap.statistikk.AppConfig
import no.nav.aap.statistikk.PrometheusProvider
import javax.sql.DataSource

data class DbConfig(
    val jdbcUrl: String,
    val userName: String,
    val password: String,
    val poolSize: Int
) {
    companion object {
        fun fraMiljøVariabler(appConfig: AppConfig): DbConfig {
            val userName: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_USERNAME")
            val password: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_PASSWORD")
            val jdbcUrl: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_JDBC_URL")

            return DbConfig(jdbcUrl, userName, password, appConfig.hikariMaxPoolSize)
        }
    }

    fun datasource(): DataSource = HikariDataSource().apply {
        jdbcUrl = this@DbConfig.jdbcUrl
        username = this@DbConfig.userName
        password = this@DbConfig.password
        connectionTestQuery = "SELECT 1"
        metricRegistry = PrometheusProvider.prometheus
    }
}