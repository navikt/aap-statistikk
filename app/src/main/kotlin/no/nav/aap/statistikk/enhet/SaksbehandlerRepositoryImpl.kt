package no.nav.aap.statistikk.enhet

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.oppgave.Saksbehandler

class SaksbehandlerRepositoryImpl(private val dbConnection: DBConnection) :
    SaksbehandlerRepository {
    companion object : RepositoryFactory<SaksbehandlerRepository> {
        override fun konstruer(connection: DBConnection): SaksbehandlerRepository {
            return SaksbehandlerRepositoryImpl(connection)
        }
    }

    override fun lagreSaksbehandler(saksbehandler: Saksbehandler): Long {
        val sql = """
            INSERT INTO saksbehandler (nav_ident) values (?)
        """.trimIndent()

        return dbConnection.executeReturnKey(sql) {
            setParams {
                setString(1, saksbehandler.ident)
            }
        }
    }

    override fun hentSaksbehandler(ident: String): Saksbehandler? {
        val sql = """
            SELECT id, nav_ident FROM saksbehandler WHERE nav_ident = ?
        """.trimIndent()

        return dbConnection.queryFirstOrNull(sql) {
            setParams {
                setString(1, ident)
            }
            setRowMapper {
                Saksbehandler(id = it.getLong("id"), ident = it.getString("nav_ident"))
            }
        }
    }
}