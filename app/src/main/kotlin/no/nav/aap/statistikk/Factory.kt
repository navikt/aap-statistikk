import no.nav.aap.komponenter.dbconnect.DBConnection

interface Factory<T> {
    fun create(dbConnection: DBConnection): T
}