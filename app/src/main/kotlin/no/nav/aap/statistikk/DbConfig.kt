package no.nav.aap.statistikk

class DbConfig(
    val database: String,
    val url: String,
    val username: String,
    val password: String
) {
    companion object {
        fun fraMiljøVariabler(): DbConfig {
            val host: String = System.getenv("NAIS_DATABASE_STATISTIKK_hendelser_HOST")
            val port: String = System.getenv("NAIS_DATABASE_STATISTIKK_hendelser_PORT")
            val database: String = System.getenv("NAIS_DATABASE_STATISTIKK_hendelser_DATABASE")
            val url = "jdbc:postgresql://$host:$port/$database"
            val username: String = System.getenv("NAIS_DATABASE_STATISTIKK_hendelser_USERNAME")
            val password: String = System.getenv("NAIS_DATABASE_STATISTIKK_hendelser_PASSWORD")

            return DbConfig(database, url, username, password)
        }

    }
}