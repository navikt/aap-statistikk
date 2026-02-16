package no.nav.aap.statistikk.db

import no.nav.aap.statistikk.AppConfig

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
}