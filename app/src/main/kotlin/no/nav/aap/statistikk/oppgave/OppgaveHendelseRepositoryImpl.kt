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
                                           endret_tidspunkt, har_hastemarkering, versjon, sendt_tid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                setBoolean(c++, hendelse.harHasteMarkering)
                setLong(c++, hendelse.versjon)
                setLocalDateTime(c++, hendelse.sendtTid)
            }
        }.also { log.info("Lagret oppgavehendelse med id $it.") }
    }

    override fun sisteVersjonForId(id: Long): Long? {
        val sql = """
            select max(versjon) from oppgave_hendelser where identifikator = ?
        """.trimIndent()

        return dbConnection.queryFirstOrNull(sql) {
            setParams { setLong(1, id) }
            setRowMapper { it.getLongOrNull("max") }
        }
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
                   har_hastemarkering,
                   versjon,
                   sendt_tid
            FROM oppgave_hendelser
            WHERE identifikator = ?
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams { setLong(1, id) }
            setRowMapper {
                OppgaveHendelse(
                    hendelse = it.getEnum("type"),
                    oppgaveId = it.getLong("identifikator"),
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
                    harHasteMarkering = it.getBooleanOrNull("har_hastemarkering"),
                    sendtTid = it.getLocalDateTime("sendt_tid"),
                    versjon = it.getLongOrNull("versjon") ?: 0L
                )
            }
        }

    }

    override fun hentEnhetOgReservasjonForAvklaringsbehov(
        behandlingReferanse: UUID,
        avklaringsbehovKode: String
    ): List<EnhetReservasjonOgTidspunkt> {
        val sql = """
            select enhet, reservert_av, mottatt_tidspunkt
            from oppgave_hendelser
            where behandling_referanse = ?
              and avklaringsbehov_kode = ?
            order by mottatt_tidspunkt desc
        """.trimIndent()

        return dbConnection.queryList(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
                setString(2, avklaringsbehovKode)
            }
            setRowMapper {
                EnhetReservasjonOgTidspunkt(
                    enhet = it.getString("enhet"),
                    reservertAv = it.getStringOrNull("reservert_av"),
                    tidspunkt = it.getLocalDateTime("mottatt_tidspunkt")
                )
            }
        }
    }

    override fun hentSisteEnhetPåBehandling(behandlingReferanse: UUID): Pair<EnhetReservasjonOgTidspunkt, String>? {
        val sql = """
            select enhet, mottatt_tidspunkt, avklaringsbehov_kode, reservert_av
            from oppgave_hendelser
            where behandling_referanse = ?
            order by mottatt_tidspunkt desc limit 1
        """.trimIndent()

        return dbConnection.queryFirstOrNull(sql) {
            setParams {
                setUUID(1, behandlingReferanse)
            }
            setRowMapper {
                EnhetReservasjonOgTidspunkt(
                    enhet = it.getString("enhet"),
                    tidspunkt = it.getLocalDateTime("mottatt_tidspunkt"),
                    reservertAv = it.getStringOrNull("reservert_av")
                ) to it.getString("avklaringsbehov_kode")
            }
        }
    }
}