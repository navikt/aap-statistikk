package no.nav.aap.statistikk.postmottak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.person.Person
import java.util.*

class PostmottakBehandlingRepository(private val dbConnection: DBConnection) {
    fun opprettBehandling(behandling: PostmottakBehandling): Long {
        val sql = """
            INSERT INTO postmottak_behandling(journalpost_id, referanse, person_id, type_behandling,
                                              mottatt_tid)
            values (?, ?, ?, ?, ?)
        """.trimIndent()

        val id = dbConnection.executeReturnKey(sql) {
            setParams {
                setLong(1, behandling.journalpostId)
                setUUID(2, behandling.referanse)
                setLong(3, behandling.person.id())
                setEnumName(4, behandling.behandlingType)
                setLocalDateTime(5, behandling.mottattTid)
            }
        }

        val sql2 = """
            insert into postmottak_behandling_historikk(postmottak_behandling_id, gjeldende,
                                                        siste_saksbehandler, gjeldende_avklaringsbehov, status,
                                                        oppdatert_tid)
            values (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dbConnection.executeBatch(sql2, behandling.endringer()) {
            setParams {
                setInt(1, id.toInt())
                setBoolean(2, it.gjeldende)
                setString(3, it.sisteSaksbehandler)
                setString(4, it.gjeldendeAvklaringsBehov)
                setString(5, it.status)
                setLocalDateTime(6, it.oppdatertTid)
            }
        }

        return id
    }

    fun hentEksisterendeBehandling(referanse: UUID): PostmottakBehandling? {
        val sql = """
            SELECT * FROM postmottak_behandling pb JOIN person p ON p.id = pb.person_id WHERE referanse = ? 
        """.trimIndent()

        val behandling = dbConnection.queryFirstOrNull(sql) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                PostmottakBehandling(
                    id = it.getLong("id"),
                    journalpostId = it.getLong("journalpost_id"),
                    person = Person(it.getString("ident"), it.getLong("person_id")),
                    referanse = it.getUUID("referanse"),
                    behandlingType = TypeBehandling.valueOf(it.getString("type_behandling")),
                    mottattTid = it.getLocalDateTime("mottatt_tid"),
                )
            }
        }

        if (behandling == null) return null

        val endringerSql = """
            select * from postmottak_behandling_historikk where postmottak_behandling_id = ?
        """.trimIndent()

        val endringer = dbConnection.queryList(endringerSql) {
            setParams {
                setInt(1, behandling.id()?.toInt())
            }
            setRowMapper {
                PostmottakOppdatering(
                    gjeldende = it.getBoolean("gjeldende"),
                    sisteSaksbehandler = it.getString("siste_saksbehandler"),
                    gjeldendeAvklaringsBehov = it.getString("gjeldende_avklaringsbehov"),
                    status = it.getString("status"),
                    oppdatertTid = it.getLocalDateTime("oppdatert_tid")
                )
            }
        }
        return behandling.medEndringer(endringer)
    }

    fun oppdaterBehandling(referanse: UUID, behandling: PostmottakOppdatering) {
        // Sett forrige oppdatering til ikke-gjeldende:
        val sqlSettIkkeGjeldende = """
            update postmottak_behandling_historikk set gjeldende = false where postmottak_behandling_id = (select b.id from postmottak_behandling b where b.referanse = ?)
        """.trimIndent()
        dbConnection.execute(sqlSettIkkeGjeldende) {
            setParams {
                setUUID(1, referanse)
            }
        }

        val sql = """
            insert into postmottak_behandling_historikk(postmottak_behandling_id, gjeldende,
                                                        siste_saksbehandler, gjeldende_avklaringsbehov, status,
                                                        oppdatert_tid)
            values ((select b.id from postmottak_behandling b where b.referanse = ?), ?, ?, ?, ?, ?) 
        """.trimIndent()

        dbConnection.execute(sql) {
            setParams {
                setUUID(1, referanse)
                setBoolean(2, behandling.gjeldende)
                setString(3, behandling.sisteSaksbehandler)
                setString(4, behandling.gjeldendeAvklaringsBehov)
                setString(5, behandling.status)
                setLocalDateTime(6, behandling.oppdatertTid)
            }
        }
    }
}