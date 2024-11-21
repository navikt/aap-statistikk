package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

typealias BehandlingId = Long

class BehandlingRepository(
    private val dbConnection: DBConnection,
    private val clock: Clock = Clock.systemDefaultZone()
) : IBehandlingRepository {

    override fun opprettBehandling(behandling: Behandling): Long {
        val behandlingId = dbConnection.executeReturnKey(
            """
INSERT INTO behandling (sak_id, referanse, type, opprettet_tid, forrige_behandling_id)
VALUES (?, ?, ?, ?, ?)"""
        ) {
            setParams {
                setLong(1, behandling.sak.id!!)
                setUUID(2, behandling.referanse)
                setString(3, behandling.typeBehandling.toString())
                setLocalDateTime(4, behandling.opprettetTid)
                setLong(5, behandling.relatertBehandlingId)
            }
        }
        oppdaterBehandling(behandling.copy(id = behandlingId))

        return behandlingId
    }

    override fun oppdaterBehandling(behandling: Behandling) {
        val behandlingId = behandling.id!!

        val versjonId = dbConnection.queryFirst(
            """
WITH ny_versjon AS (
    INSERT INTO versjon (versjon)
        VALUES (?)
        ON CONFLICT DO NOTHING
        RETURNING id)
SELECT COALESCE(
               (SELECT id FROM ny_versjon),
               (SELECT id FROM versjon WHERE versjon.versjon = ?)
       ) AS id;
"""
        ) {
            setParams {
                setString(1, behandling.versjon.verdi)
                setString(2, behandling.versjon.verdi)
            }
            setRowMapper { row -> row.getLong("id") }
        }

        dbConnection.execute("UPDATE behandling_historikk SET gjeldende = FALSE where behandling_id = ?") {
            setParams { setLong(1, behandlingId) }
        }

        dbConnection.executeBatch(
            "INSERT INTO person (ident) VALUES (?) ON CONFLICT DO NOTHING",
            behandling.relaterteIdenter
        ) {
            setParams { ident ->
                setString(1, ident)
            }
        }

        val historikkId = dbConnection.executeReturnKey(
            """INSERT INTO behandling_historikk (behandling_id,
                                  versjon_id, gjeldende, oppdatert_tid, mottatt_tid,
                                  status, siste_saksbehandler, gjeldende_avklaringsbehov,
                                  soknadsformat, venteaarsak, steggruppe)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ) {
            setParams {
                var c = 1
                setLong(c++, behandlingId)
                setLong(c++, versjonId)
                setBoolean(c++, true)
                setLocalDateTime(c++, LocalDateTime.now(clock))
                setLocalDateTime(c++, behandling.mottattTid)
                setString(c++, behandling.status.name)
                setString(c++, behandling.sisteSaksbehandler)
                setString(c++, behandling.gjeldendeAvklaringsBehov)
                setEnumName(c++, behandling.søknadsformat)
                setString(c++, behandling.venteÅrsak)
                setEnumName(c, behandling.gjeldendeStegGruppe)
            }
        }

        dbConnection.executeBatch(
            """INSERT INTO relaterte_personer (behandling_id, person_id)
SELECT $historikkId, id
FROM person
WHERE ident = ?""", behandling.relaterteIdenter
        ) {
            setParams {
                setString(1, it)
            }
        }
    }

    override fun hent(referanse: UUID): Behandling? {
        return dbConnection.queryFirstOrNull(
            """SELECT b.id                         as b_id,
       b.referanse                  as b_referanse,
       b.type                       as b_type,
       b.opprettet_tid              as b_opprettet_tid,
       b.forrige_behandling_id      as b_forrige_behandling_id,
       s.id                         as s_id,
       s.saksnummer                 as s_saksnummer,
       sh.oppdatert_tid             as sh_oppdatert_tid,
       sh.sak_status                as sh_sak_status,
       sh.id                        as sh_id,
       p.ident                      as p_ident,
       p.id                         as p_id,
       bh.status                    as bh_status,
       bh.versjon_id                as bh_versjon_id,
       bh.mottatt_tid               as bh_mottatt_tid,
       bh.id                        as bh_id,
       bh.siste_saksbehandler       as bh_siste_saksbehandler,
       bh.venteaarsak               as bh_venteaarsak,
       bh.gjeldende_avklaringsbehov as bh_gjeldende_avklaringsbehov,
       bh.soknadsformat             as bh_soknadsformat,
       bh.steggruppe                as bh_steggruppe,
       v.versjon                    as v_versjon,
       rp.rp_ident                  as rp_ident
FROM behandling b
         JOIN sak s on b.sak_id = s.id
         JOIN (SELECT * FROM sak_historikk sh WHERE gjeldende = TRUE) sh on s.id = sh.sak_id
         JOIN person p on p.id = s.person_id
         JOIN (SELECT * FROM behandling_historikk bh WHERE bh.gjeldende = TRUE) bh
              on b.id = bh.behandling_id
         JOIN versjon v on v.id = bh.versjon_id
         LEFT JOIN (SELECT rp.behandling_id, array_agg(pr.ident) as rp_ident
                    FROM relaterte_personer rp
                             JOIN person pr ON rp.person_id = pr.id
                    GROUP BY rp.behandling_id) rp
                   on rp.behandling_id = bh.id
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

    private val hentMedId = """SELECT b.id                         as b_id,
       b.referanse                  as b_referanse,
       b.type                       as b_type,
       b.opprettet_tid              as b_opprettet_tid,
       b.forrige_behandling_id      as b_forrige_behandling_id,
       s.id                         as s_id,
       s.saksnummer                 as s_saksnummer,
       sh.oppdatert_tid             as sh_oppdatert_tid,
       sh.sak_status                as sh_sak_status,
       sh.id                        as sh_id,
       p.ident                      as p_ident,
       p.id                         as p_id,
       bh.status                    as bh_status,
       bh.versjon_id                as bh_versjon_id,
       bh.mottatt_tid               as bh_mottatt_tid,
       bh.siste_saksbehandler       as bh_siste_saksbehandler,
       bh.venteaarsak               as bh_venteaarsak,
       bh.gjeldende_avklaringsbehov as bh_gjeldende_avklaringsbehov,
       bh.soknadsformat             as bh_soknadsformat,
       bh.steggruppe                as bh_steggruppe,
       bh.id                        as bh_id,
       v.versjon                    as v_versjon,
       rp.rp_ident                  as rp_ident
FROM behandling b
         JOIN sak s on b.sak_id = s.id
         JOIN (SELECT * FROM sak_historikk sh WHERE gjeldende = TRUE) sh on s.id = sh.sak_id
         JOIN person p on p.id = s.person_id
         JOIN (SELECT * FROM behandling_historikk WHERE gjeldende = TRUE) bh
              on bh.behandling_id = b.id
         JOIN versjon v on v.id = bh.versjon_id
         LEFT JOIN (SELECT rp.behandling_id, array_agg(pr.ident) as rp_ident
                    FROM relaterte_personer rp
                             JOIN person pr ON rp.person_id = pr.id
                    GROUP BY rp.behandling_id) rp
                   on rp.behandling_id = bh.id
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

    override fun tellFullførteBehandlinger(): Long {
        return dbConnection.queryFirst("""SELECT count(*) FROM behandling_historikk where gjeldende = TRUE AND status = 'AVSLUTTET'""") {
            setRowMapper {
                it.getLong("count")
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
            ),
            sistOppdatert = it.getLocalDateTime("sh_oppdatert_tid"),
            snapShotId = it.getLong("sh_id"),
            sakStatus = it.getEnum("sh_sak_status")
        ),
        typeBehandling = it.getString("b_type").let { TypeBehandling.valueOf(it) },
        status = it.getString("bh_status").let { BehandlingStatus.valueOf(it) },
        opprettetTid = it.getLocalDateTime("b_opprettet_tid"),
        mottattTid = it.getLocalDateTime("bh_mottatt_tid"),
        versjon = Versjon(verdi = it.getString("v_versjon"), id = it.getLong("bh_versjon_id")),
        søknadsformat = it.getEnum("bh_soknadsformat"),
        sisteSaksbehandler = it.getStringOrNull("bh_siste_saksbehandler")?.ifBlank { null },
        relaterteIdenter = it.getArray("rp_ident", String::class),
        relatertBehandlingId = it.getLongOrNull("b_forrige_behandling_id"),
        snapShotId = it.getLong("bh_id"),
        gjeldendeAvklaringsBehov = it.getStringOrNull("bh_gjeldende_avklaringsbehov")
            ?.ifBlank { null },
        venteÅrsak = it.getStringOrNull("bh_venteaarsak")?.ifBlank { null },
        gjeldendeStegGruppe = it.getEnumOrNull("bh_steggruppe")
    )
}