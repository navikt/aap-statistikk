package no.nav.aap.statistikk

class DbConfig(
    val database: String,
    val url: String,
    val username: String,
    val password: String
) {
    companion object {
        fun fraMiljøVariabler(): DbConfig {
            val host: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_HOST")
            val port: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_PORT")
            val database: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_DATABASE")
            val url = "jdbc:postgresql://$host:$port/$database"
            val username: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_USERNAME")
            val password: String = System.getenv("NAIS_DATABASE_STATISTIKK_HENDELSER_PASSWORD")

            return DbConfig(database, url, username, password)
        }

    }
}