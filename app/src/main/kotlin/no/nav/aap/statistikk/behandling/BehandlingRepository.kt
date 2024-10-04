package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

typealias BehandlingId = Long

class BehandlingRepository(
    private val dbConnection: DBConnection,
    private val clock: Clock = Clock.systemUTC()
) : IBehandlingRepository {
    override fun lagre(behandling: Behandling): BehandlingId {
        val behandlingId = dbConnection.executeReturnKey(
            """
INSERT INTO behandling (sak_id, referanse, type, opprettet_tid)
VALUES (?, ?, ?, ?)"""
        ) {
            setParams {
                setLong(1, behandling.sak.id!!)
                setUUID(2, behandling.referanse)
                setString(3, behandling.typeBehandling.toString())
                setLocalDateTime(4, behandling.opprettetTid)
            }
        }

        val versjonId = dbConnection.executeReturnKey(
            """INSERT INTO versjon (versjon)
                            VALUES (?)
                            ON CONFLICT (versjon)
                                DO NOTHING
                            RETURNING id;"""
        ) {
            setParams {
                setString(1, behandling.versjon.verdi)
            }
        }

        dbConnection.execute("UPDATE behandling_historikk SET gjeldende = FALSE where behandling_id = ?") {
            setParams { setLong(1, behandlingId) }
        }

        dbConnection.executeReturnKey(
            """
INSERT INTO behandling_historikk (behandling_id, versjon_id, gjeldende, oppdatert_tid, mottatt_tid,
                                  status)
VALUES (?, ?, ?, ?, ?, ?)""",
        ) {
            setParams {
                var c = 1
                setLong(c++, behandlingId)
                setLong(c++, versjonId)
                setBoolean(c++, true)
                setLocalDateTime(c++, LocalDateTime.now(clock))
                setLocalDateTime(c++, behandling.mottattTid)
                setString(c, behandling.status.name)
            }
        }

        return behandlingId
    }

    override fun hent(referanse: UUID): Behandling? {
        return dbConnection.queryFirstOrNull(
            """
SELECT b.id            as b_id,
       b.referanse     as b_referanse,
       b.type          as b_type,
       b.opprettet_tid as b_opprettet_tid,
       s.id            as s_id,
       s.saksnummer    as s_saksnummer,
       p.ident         as p_ident,
       p.id            as p_id,
       bh.status       as bh_status,
       bh.versjon_id   as bh_versjon_id,
       bh.mottatt_tid  as bh_mottatt_tid,
       v.versjon       as v_versjon
FROM behandling b
         JOIN sak s on b.sak_id = s.id
         JOIN person p on p.id = s.person_id
         JOIN (SELECT * FROM behandling_historikk bh WHERE bh.gjeldende = TRUE) bh
              on b.id = bh.behandling_id
         JOIN versjon v on v.id = bh.versjon_id
WHERE b.referanse = ?"""
        ) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                mapBehandling(it)
            }
        }
    }

    private val hentMedId = """SELECT b.id            as b_id,
       b.referanse     as b_referanse,
       b.type          as b_type,
       b.opprettet_tid as b_opprettet_tid,
       s.id            as s_id,
       s.saksnummer    as s_saksnummer,
       p.ident         as p_ident,
       p.id            as p_id,
       bh.status       as bh_status,
       bh.versjon_id   as bh_versjon_id,
       bh.mottatt_tid  as bh_mottatt_tid,
       v.versjon       as v_versjon
FROM behandling b
         JOIN sak s on b.sak_id = s.id
         JOIN person p on p.id = s.person_id
         JOIN (SELECT * FROM behandling_historikk WHERE gjeldende = TRUE) bh
              on bh.behandling_id = b.id
         JOIN versjon v on v.id = bh.versjon_id
WHERE b.id = ?"""

    override fun hent(id: Long): Behandling {
        return dbConnection.queryFirst(
            hentMedId
        ) {
            setParams {
                setLong(1, id)
            }
            setRowMapper {
                mapBehandling(it)
            }
        }
    }

    override fun hentEllerNull(id: Long): Behandling? {
        return dbConnection.queryFirstOrNull(hentMedId) {
            setParams {
                setLong(1, id)
            }
            setRowMapper {
                mapBehandling(it)
            }
        }
    }

    private fun mapBehandling(it: Row) = Behandling(
        id = it.getLong("b_id"),
        referanse = it.getUUID("b_referanse"),
        sak = Sak(
            id = it.getLong("s_id"),
            saksnummer = it.getString("s_saksnummer"),
            person = Person(
                ident = it.getString("p_ident"),
                id = it.getLong("p_id"),
            )
        ),
        typeBehandling = it.getString("b_type").let { TypeBehandling.valueOf(it) },
        opprettetTid = it.getLocalDateTime("b_opprettet_tid"),
        mottattTid = it.getLocalDateTime("bh_mottatt_tid"),
        versjon = Versjon(verdi = it.getString("v_versjon"), id = it.getLong("bh_versjon_id")),
        status = it.getString("bh_status").let { BehandlingStatus.valueOf(it) }
    )
}