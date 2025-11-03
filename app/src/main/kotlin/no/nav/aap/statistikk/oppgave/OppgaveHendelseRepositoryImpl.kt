package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.repository.RepositoryFactory
import org.slf4j.LoggerFactory
import java.util.*

class OppgaveHendelseRepositoryImpl(private val dbConnection: DBConnection) :
    OppgaveHendelseRepository {
    companion object : RepositoryFactory<OppgaveHendelseRepository> {
        override fun konstruer(connection: DBConnection): OppgaveHendelseRepository {
            return OppgaveHendelseRepositoryImpl(connection)
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun lagreHendelse(hendelse: OppgaveHendelse): Long {
        val sql = """
            INSERT INTO oppgave_hendelser (type, identifikator, mottatt_tidspunkt, person_ident, saksnummer,
                                           behandling_referanse,
                                           journalpost_id, enhet, avklaringsbehov_kode, status, reservert_av,
                                           reservert_tidspunkt, opprettet_tidspunkt, endret_av,
                                           endret_tidspunkt, har_hastemarkering)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return dbConnection.executeReturnKey(sql) {
            var c = 1
            setParams {
                setEnumName(c++, hendelse.hendelse)
                setLong(c++, hendelse.oppgaveId)
                setLocalDateTime(c++, hendelse.mottattTidspunkt)
                setString(c++, hendelse.personIdent)
                setString(c++, hendelse.saksnummer)
                setUUID(c++, hendelse.behandlingRef)
                setLong(c++, hendelse.journalpostId)
                setString(c++, hendelse.enhet)
                setString(c++, hendelse.avklaringsbehovKode)
                setEnumName(c++, hendelse.status)
                setString(c++, hendelse.reservertAv)
                setLocalDateTime(c++, hendelse.reservertTidspunkt)
                setLocalDateTime(c++, hendelse.opprettetTidspunkt)
                setString(c++, hendelse.endretAv)
                setLocalDateTime(c++, hendelse.endretTidspunkt)
                setBoolean(c, hendelse.harHasteMarkering)
            }
        }.also { log.info("Lagret oppgavehendelse med id $it.") }
    }

    override fun hentHendelserForId(id: Long): List<OppgaveHendelse> {
        val sql = """
            SELECT type,
                   identifikator,
                   mottatt_tidspunkt,
                   person_ident,
                   saksnummer,
                   behandling_referanse,
                   journalpost_id,
                   enhet,
                   avklaringsbehov_kode,
                   status,
                   reservert_av,
                   reservert_tidspunkt,
                   opprettet_tidspunkt,
                   endret_av,
                   endret_tidspunkt,
                   har_hastemarkering
            FROM oppgave_hendelser
            WHERE identifikator = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams { setLong(1, id) }
            setRowMapper {
                OppgaveHendelse(
                    hendelse = it.getEnum("type"),
                    mottattTidspunkt = it.getLocalDateTime("mottatt_tidspunkt"),
                    personIdent = it.getStringOrNull("person_ident"),
                    saksnummer = it.getStringOrNull("saksnummer"),
                    behandlingRef = it.getUUIDOrNull("behandling_referanse"),
                    journalpostId = it.getLongOrNull("journalpost_id"),
                    enhet = it.getString("enhet"),
                    avklaringsbehovKode = it.getString("avklaringsbehov_kode"),
                    status = it.getEnum("status"),
                    reservertAv = it.getStringOrNull("reservert_av"),
                    reservertTidspunkt = it.getLocalDateTimeOrNull("reservert_tidspunkt"),
                    opprettetTidspunkt = it.getLocalDateTime("opprettet_tidspunkt"),
                    endretAv = it.getStringOrNull("endret_av"),
                    endretTidspunkt = it.getLocalDateTimeOrNull("endret_tidspunkt"),
                    oppgaveId = it.getLong("identifikator"),
                    harHasteMarkering = it.getBooleanOrNull("har_hastemarkering")
                )
            }
        }

    }

    override fun hentEnhetForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: String
    ): List<EnhetOgTidspunkt> {
        val sql = """
            select enhet, opprettet_tidspunkt
            from oppgave_hendelser
            where behandling_referanse = ?
              and avklaringsbehov_kode = ?
            order by opprettet_tidspunkt desc
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
                setString(2, avklaringsbehovKode)
            }
            setRowMapper {
                EnhetOgTidspunkt(
                    enhet = it.getString("enhet"),
                    tidspunkt = it.getLocalDateTime("opprettet_tidspunkt")
                )
            }
        }
    }
}