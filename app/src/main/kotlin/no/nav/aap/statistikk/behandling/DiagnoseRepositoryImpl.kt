package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import no.nav.aap.statistikk.avsluttetbehandling.Diagnoser
import org.slf4j.LoggerFactory
import java.util.*

data class DiagnoseEntity(
    val behandlingReferanse: UUID,
    val kodeverk: String,
    val diagnosekode: String,
    val bidiagnoser: List<String>
) {
    companion object {
        fun fraDomene(domene: Diagnoser, behandlingReferanse: UUID): DiagnoseEntity {
            return DiagnoseEntity(
                behandlingReferanse = behandlingReferanse,
                kodeverk = domene.kodeverk,
                diagnosekode = domene.diagnosekode,
                bidiagnoser = domene.bidiagnoser
            )
        }
    }
}

class DiagnoseRepositoryImpl(private val dbConnection: DBConnection) : DiagnoseRepository {
    private val logger = LoggerFactory.getLogger(DiagnoseRepositoryImpl::class.java)

    companion object : RepositoryFactory<DiagnoseRepository> {
        override fun konstruer(connection: DBConnection): DiagnoseRepository {
            return DiagnoseRepositoryImpl(connection)
        }
    }

    override fun lagre(diagnoseEntity: DiagnoseEntity): Long {
        val sql = """
INSERT INTO DIAGNOSE (behandling_id, kodeverk, diagnosekode, bidiagnoser)
VALUES ((SELECT b.id
         FROM behandling b
                  join behandling_referanse br on b.referanse_id = br.id
         WHERE br.referanse = ?),
        ?,
        ?,
        ?);

        """.trimIndent()

        val id = dbConnection.executeReturnKey(sql) {
            setParams {
                setUUID(1, diagnoseEntity.behandlingReferanse)
                setString(2, diagnoseEntity.kodeverk)
                setString(3, diagnoseEntity.diagnosekode)
                setArray(4, diagnoseEntity.bidiagnoser)
            }
        }

        logger.info("Lagret diagnose med ID: $id.")

        return id
    }

    override fun hentForBehandling(behandlingReferanse: UUID): DiagnoseEntity? {
        val sql = """
         SELECT d.kodeverk as d_kodeverk, d.diagnosekode as d_diagnosekode, d.bidiagnoser as d_bidiagnoser
         from diagnose d
         where d.behandling_id = (SELECT b.id
                                  FROM behandling b
                                           join behandling_referanse br on b.referanse_id = br.id
                                  WHERE br.referanse = ?)
        """.trimIndent()

        return dbConnection.queryFirstOrNull(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
            }
            setRowMapper {
                DiagnoseEntity(
                    behandlingReferanse = behandlingReferanse,
                    kodeverk = it.getString("d_kodeverk"),
                    diagnosekode = it.getString("d_diagnosekode"),
                    bidiagnoser = it.getArray("d_bidiagnoser", String::class)
                )
            }
        }
    }

}