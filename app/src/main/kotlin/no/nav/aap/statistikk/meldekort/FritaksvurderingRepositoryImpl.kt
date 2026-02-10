package no.nav.aap.statistikk.meldekort

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.behandling.BehandlingId

class FritaksvurderingRepositoryImpl(private val dbConnection: DBConnection) :
    FritaksvurderingRepository {

    companion object : RepositoryFactory<FritaksvurderingRepository> {
        override fun konstruer(connection: DBConnection): FritaksvurderingRepository {
            return FritaksvurderingRepositoryImpl(connection)
        }
    }

    override fun lagre(
        behandlingId: BehandlingId,
        vurderinger: List<Fritakvurdering>
    ) {
        dbConnection.executeBatch(
            """INSERT INTO fritaksvurdering (behandling_id, har_fritak, fra_dato, til_dato)
VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING""".trimIndent(),
            vurderinger
        ) {
            setParams {
                setLong(1, behandlingId.id)
                setBoolean(2, it.harFritak)
                setLocalDate(3, it.fraDato)
                setLocalDate(4, it.tilDato)
            }
        }
    }

    override fun hentFritaksvurderinger(behandlingId: BehandlingId): List<Fritakvurdering> {
        return dbConnection.queryList(
            """SELECT har_fritak, fra_dato, til_dato 
               FROM fritaksvurdering
               WHERE behandling_id = ?""".trimIndent()
        ) {
            setParams {
                setLong(1, behandlingId.id)
            }
            setRowMapper {
                Fritakvurdering(
                    harFritak = it.getBoolean("har_fritak"),
                    fraDato = it.getLocalDate("fra_dato"),
                    tilDato = it.getLocalDateOrNull("til_dato")
                )
            }
        }
    }
}