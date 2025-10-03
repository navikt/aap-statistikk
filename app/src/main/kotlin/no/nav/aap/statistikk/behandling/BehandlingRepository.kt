package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.Saksnummer
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@JvmInline
value class BehandlingId(val id: Long)

class BehandlingRepository(
    private val dbConnection: DBConnection, private val clock: Clock = Clock.systemDefaultZone()
) : IBehandlingRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun opprettBehandling(behandling: Behandling): BehandlingId {
        val behandlingReferanseQuery = """WITH ny_ref AS (
    INSERT INTO behandling_referanse (referanse)
        VALUES (?)
        ON CONFLICT DO NOTHING
        RETURNING id)
SELECT COALESCE(
               (SELECT id FROM ny_ref),
               (SELECT id FROM behandling_referanse WHERE behandling_referanse.referanse = ?)
       ) AS id;"""

        val id = dbConnection.queryFirst(behandlingReferanseQuery) {
            setParams {
                setUUID(1, behandling.referanse)
                setUUID(2, behandling.referanse)
            }
            setRowMapper { row -> row.getLong("id") }
        }

        val behandlingId = dbConnection.executeReturnKey(
            """INSERT INTO behandling (sak_id, referanse_id, type, opprettet_tid, forrige_behandling_id,
                        aarsaker_til_behandling)
VALUES (?, ?, ?, ?, ?, ?)"""
        ) {
            setParams {
                setLong(1, behandling.sak.id!!)
                setLong(2, id)
                setString(3, behandling.typeBehandling.toString())
                setLocalDateTime(4, behandling.opprettetTid)
                setLong(5, behandling.relatertBehandlingId?.id)
                setArray(6, behandling.årsaker.map { it.name })
            }
        }
        oppdaterBehandling(behandling.copy(id = behandlingId.let(::BehandlingId)))

        return BehandlingId(behandlingId)
    }

    override fun oppdaterBehandling(behandling: Behandling) {
        val behandlingId = behandling.id!!

        val versjonId = lagreOgHentVersjonId(behandling.versjon)

        dbConnection.execute("UPDATE behandling_historikk SET gjeldende = FALSE where behandling_id = ?") {
            setParams { setLong(1, behandlingId.id) }
        }

        dbConnection.executeBatch(
            "INSERT INTO person (ident) VALUES (?) ON CONFLICT DO NOTHING",
            behandling.relaterteIdenter
        ) {
            setParams { ident ->
                setString(1, ident)
            }
        }

        dbConnection.execute("UPDATE behandling SET aarsaker_til_behandling = ? WHERE id = ?") {
            setParams {
                setArray(1, behandling.årsaker.map { it.name })
                setLong(2, behandlingId.id)
            }
        }

        val historikkId = dbConnection.executeReturnKey(
            """INSERT INTO behandling_historikk (behandling_id,
                                  versjon_id, gjeldende, oppdatert_tid, mottatt_tid,
                                  vedtak_tidspunkt, ansvarlig_beslutter,
                                  status, siste_saksbehandler, gjeldende_avklaringsbehov,
                                  gjeldende_avklaringsbehov_status,
                                  soknadsformat, venteaarsak, steggruppe, retur_aarsak, resultat,
                                  hendelsestidspunkt, slettet)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ) {
            setParams {
                var c = 1
                setLong(c++, behandlingId.id)
                setLong(c++, versjonId)
                setBoolean(c++, true)
                setLocalDateTime(c++, LocalDateTime.now(clock))
                setLocalDateTime(c++, behandling.mottattTid)
                setLocalDateTime(c++, behandling.vedtakstidspunkt)
                setString(c++, behandling.ansvarligBeslutter)
                setString(c++, behandling.status.name)
                setString(c++, behandling.sisteSaksbehandler)
                setString(c++, behandling.gjeldendeAvklaringsBehov)
                setString(c++, behandling.gjeldendeAvklaringsbehovStatus?.name)
                setEnumName(c++, behandling.søknadsformat)
                setString(c++, behandling.venteÅrsak)
                setEnumName(c++, behandling.gjeldendeStegGruppe)
                setString(c++, behandling.returÅrsak)
                setEnumName(c++, behandling.resultat)
                setLocalDateTime(c++, behandling.oppdatertTidspunkt)
                setBoolean(c++, false)
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

    private fun lagreOgHentVersjonId(versjon: Versjon): Long {
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
                setString(1, versjon.verdi)
                setString(2, versjon.verdi)
            }
            setRowMapper { row -> row.getLong("id") }
        }
        return versjonId
    }

    override fun invaliderOgLagreNyHistorikk(behandling: Behandling) {
        val merkSomSlettetSql = """
            UPDATE behandling_historikk
            SET slettet  = TRUE,
                gjeldende= FALSE
            WHERE behandling_id = ?
        """.trimIndent()

        dbConnection.execute(merkSomSlettetSql) {
            setParams {
                setLong(1, behandling.id!!.id)
            }
        }

        val versjonId = lagreOgHentVersjonId(behandling.versjon)
        val oppdateringer = behandling.hendelsesHistorikk()

        dbConnection.executeBatch(
            """INSERT INTO behandling_historikk (behandling_id,
                                  versjon_id, gjeldende, oppdatert_tid, mottatt_tid,
                                  vedtak_tidspunkt, ansvarlig_beslutter,
                                  status, siste_saksbehandler, gjeldende_avklaringsbehov,
                                  gjeldende_avklaringsbehov_status,
                                  soknadsformat, venteaarsak, steggruppe, retur_aarsak, resultat,
                                  hendelsestidspunkt, slettet)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oppdateringer.mapIndexed { index, behandling -> Pair(behandling, index) }
        ) {
            setParams { (behandling, idx) ->
                var c = 1
                setLong(c++, behandling.id!!.id)
                setLong(c++, versjonId)
                setBoolean(c++, idx == oppdateringer.lastIndex)
                setLocalDateTime(c++, LocalDateTime.now(clock))
                setLocalDateTime(c++, behandling.mottattTid)
                setLocalDateTime(c++, behandling.vedtakstidspunkt)
                setString(c++, behandling.ansvarligBeslutter)
                setString(c++, behandling.status.name)
                setString(c++, behandling.sisteSaksbehandler)
                setString(c++, behandling.gjeldendeAvklaringsBehov)
                setString(c++, behandling.gjeldendeAvklaringsbehovStatus?.name)
                setEnumName(c++, behandling.søknadsformat)
                setString(c++, behandling.venteÅrsak)
                setEnumName(c++, behandling.gjeldendeStegGruppe)
                setString(c++, behandling.returÅrsak)
                setEnumName(c++, behandling.resultat)
                setLocalDateTime(c++, behandling.oppdatertTidspunkt)
                setBoolean(c++, false)
            }
        }
        log.info("Satte inn ${oppdateringer.size} hendelser for behandling ${behandling.id} med versjon $versjonId.")
    }

    override fun hent(referanse: UUID): Behandling? {
        val behandling = dbConnection.queryFirstOrNull(
            """
SELECT b.id                                as b_id,
       br.referanse                        as br_referanse,
       b.type                              as b_type,
       b.opprettet_tid                     as b_opprettet_tid,
       b.forrige_behandling_id             as b_forrige_behandling_id,
       b.aarsaker_til_behandling           as b_aarsaker_til_behandling,
       s.id                                as s_id,
       s.saksnummer                        as s_saksnummer,
       sh.oppdatert_tid                    as sh_oppdatert_tid,
       sh.sak_status                       as sh_sak_status,
       sh.id                               as sh_id,
       p.ident                             as p_ident,
       p.id                                as p_id,
       bh.status                           as bh_status,
       bh.versjon_id                       as bh_versjon_id,
       bh.mottatt_tid                      as bh_mottatt_tid,
       bh.hendelsestidspunkt               as bh_hendelsestidspunkt,
       bh.vedtak_tidspunkt                 as bh_vedtak_tidspunkt,
       bh.ansvarlig_beslutter              as bh_ansvarlig_beslutter,
       bh.siste_saksbehandler              as bh_siste_saksbehandler,
       bh.venteaarsak                      as bh_venteaarsak,
       bh.retur_aarsak                     as bh_retur_aarsak,
       bh.gjeldende_avklaringsbehov        as bh_gjeldende_avklaringsbehov,
       bh.gjeldende_avklaringsbehov_status as bh_gjeldende_avklaringsbehov_status,
       bh.resultat                         as bh_resultat,
       bh.soknadsformat                    as bh_soknadsformat,
       bh.steggruppe                       as bh_steggruppe,
       bh.id                               as bh_id,
       v.versjon                           as v_versjon,
       rp.rp_ident                         as rp_ident
FROM behandling b
         JOIN behandling_referanse br on b.referanse_id = br.id
         JOIN sak s on b.sak_id = s.id
         JOIN LATERAL (SELECT *
                       FROM sak_historikk sh
                       WHERE gjeldende = TRUE
                         and sh.sak_id = s.id) sh on s.id = sh.sak_id
         JOIN person p on p.id = s.person_id
         JOIN LATERAL (SELECT *
                       FROM behandling_historikk
                       WHERE gjeldende = TRUE AND SLETTET = FALSE
                         AND behandling_historikk.behandling_id = b.id) bh
              on bh.behandling_id = b.id
         JOIN versjon v on v.id = bh.versjon_id
         LEFT JOIN LATERAL (SELECT rp.behandling_id, array_agg(pr.ident) as rp_ident
                            FROM relaterte_personer rp
                                     JOIN person pr ON rp.person_id = pr.id
                            WHERE rp.behandling_id = bh.behandling_id
                            GROUP BY rp.behandling_id) rp
                   on rp.behandling_id = bh.id
WHERE br.referanse = ?"""
        ) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                mapBehandling(it)
            }
        }?.let { behandling ->
            behandling.copy(hendelser = hentBehandlingHistorikk(behandling))
        }
        return behandling
    }

    private val hentMedId = """
SELECT b.id                                as b_id,
       br.referanse                        as br_referanse,
       b.type                              as b_type,
       b.opprettet_tid                     as b_opprettet_tid,
       b.forrige_behandling_id             as b_forrige_behandling_id,
       b.aarsaker_til_behandling           as b_aarsaker_til_behandling,
       s.id                                as s_id,
       s.saksnummer                        as s_saksnummer,
       sh.oppdatert_tid                    as sh_oppdatert_tid,
       sh.sak_status                       as sh_sak_status,
       sh.id                               as sh_id,
       p.ident                             as p_ident,
       p.id                                as p_id,
       bh.status                           as bh_status,
       bh.versjon_id                       as bh_versjon_id,
       bh.mottatt_tid                      as bh_mottatt_tid,
       bh.hendelsestidspunkt               as bh_hendelsestidspunkt,
       bh.vedtak_tidspunkt                 as bh_vedtak_tidspunkt,
       bh.ansvarlig_beslutter              as bh_ansvarlig_beslutter,
       bh.siste_saksbehandler              as bh_siste_saksbehandler,
       bh.venteaarsak                      as bh_venteaarsak,
       bh.retur_aarsak                     as bh_retur_aarsak,
       bh.gjeldende_avklaringsbehov        as bh_gjeldende_avklaringsbehov,
       bh.gjeldende_avklaringsbehov_status as bh_gjeldende_avklaringsbehov_status,
       bh.resultat                         as bh_resultat,
       bh.soknadsformat                    as bh_soknadsformat,
       bh.steggruppe                       as bh_steggruppe,
       bh.id                               as bh_id,
       v.versjon                           as v_versjon,
       rp.rp_ident                         as rp_ident
FROM behandling b
         JOIN behandling_referanse br on b.referanse_id = br.id
         JOIN sak s on b.sak_id = s.id
         JOIN LATERAL (SELECT *
                       FROM sak_historikk sh
                       WHERE gjeldende = TRUE
                         and sh.sak_id = s.id) sh on s.id = sh.sak_id
         JOIN person p on p.id = s.person_id
         JOIN LATERAL (SELECT *
                       FROM behandling_historikk
                       WHERE gjeldende = TRUE
                         AND SLETTET = FALSE
                         AND behandling_historikk.behandling_id = b.id) bh
              on bh.behandling_id = b.id
         JOIN versjon v on v.id = bh.versjon_id
         LEFT JOIN LATERAL (SELECT rp.behandling_id, array_agg(pr.ident) as rp_ident
                            FROM relaterte_personer rp
                                     JOIN person pr ON rp.person_id = pr.id
                            WHERE rp.behandling_id = bh.behandling_id
                            GROUP BY rp.behandling_id) rp
                   on rp.behandling_id = bh.id
WHERE b.id = ?"""

    override fun hent(id: BehandlingId): Behandling {
        val behandling = dbConnection.queryFirstOrNull(hentMedId) {
            setParams {
                setLong(1, id.id)
            }
            setRowMapper {
                mapBehandling(it)
            }
        }?.let {
            it.copy(hendelser = hentBehandlingHistorikk(it))
        }

        return checkNotNull(behandling) { "Fant ikke behandling med id $id" }
    }

    private fun hentBehandlingHistorikk(behandling: Behandling): List<BehandlingHendelse> = run {
        val historikkSpørring = """
       select bh.siste_saksbehandler              as bh_siste_saksbehandler,
              bh.oppdatert_tid                    as bh_opprettet_tidspunkt,
              bh.hendelsestidspunkt               as bh_hendelsestidspunkt,
              bh.venteaarsak                      as bh_venteaarsak,
              bh.retur_aarsak                     as bh_retur_aarsak,
              bh.gjeldende_avklaringsbehov        as bh_gjeldende_avklaringsbehov,
              bh.gjeldende_avklaringsbehov_status as bh_gjeldende_avklaringsbehov_status,
              bh.steggruppe                       as bh_steggruppe,
              bh.soknadsformat                    as bh_soknadsformat,
              bh.resultat                         as bh_resultat,
              bh.status                           as bh_status,
              bh.ansvarlig_beslutter              as bh_ansvarlig_beslutter,
              bh.vedtak_tidspunkt                 as bh_vedtak_tidspunkt,
              bh.mottatt_tid                      as bh_mottatt_tid,
              bh.versjon_id                       as bh_versjon_id,
              v.versjon                           as bh_versjon
       from behandling_historikk bh
                join versjon v on bh.versjon_id = v.id
       where bh.behandling_id = ? and slettet = false
       order by bh.hendelsestidspunkt
                """.trimIndent()

        dbConnection.queryList(historikkSpørring) {
            setParams { setLong(1, behandling.id!!.id) }
            setRowMapper {
                BehandlingHendelse(
                    tidspunkt = it.getLocalDateTime("bh_opprettet_tidspunkt"),
                    hendelsesTidspunkt = it.getLocalDateTime("bh_hendelsestidspunkt"),
                    avklaringsBehov = it.getStringOrNull("bh_gjeldende_avklaringsbehov"),
                    avklaringsbehovStatus = it.getEnumOrNull("bh_gjeldende_avklaringsbehov_status"),
                    steggruppe = it.getEnumOrNull("bh_steggruppe"),
                    venteÅrsak = it.getStringOrNull("bh_venteaarsak"),
                    returÅrsak = it.getStringOrNull("bh_retur_aarsak"),
                    resultat = it.getEnumOrNull("bh_resultat"),
                    saksbehandler = it.getStringOrNull("bh_siste_saksbehandler")
                        ?.let { saksbehandler ->
                            Saksbehandler(
                                ident = saksbehandler
                            )
                        },
                    ansvarligBeslutter = it.getStringOrNull("bh_ansvarlig_beslutter"),
                    vedtakstidspunkt = it.getLocalDateTimeOrNull("bh_vedtak_tidspunkt"),
                    status = it.getEnum("bh_status"),
                    mottattTid = it.getLocalDateTime("bh_mottatt_tid"),
                    søknadsformat = it.getEnum("bh_soknadsformat"),
                    versjon = Versjon(
                        id = it.getLong("bh_versjon_id"), verdi = it.getString("bh_versjon")
                    )
                )
            }
        }
    }

    override fun hentEllerNull(id: BehandlingId): Behandling? {
        return dbConnection.queryFirstOrNull(hentMedId) {
            setParams {
                setLong(1, id.id)
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
        id = it.getLong("b_id").let(::BehandlingId),
        referanse = it.getUUID("br_referanse"),
        sak = Sak(
            id = it.getLong("s_id"),
            saksnummer = Saksnummer(it.getString("s_saksnummer")),
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
        vedtakstidspunkt = it.getLocalDateTimeOrNull("bh_vedtak_tidspunkt"),
        ansvarligBeslutter = it.getStringOrNull("bh_ansvarlig_beslutter")?.ifBlank { null },
        versjon = Versjon(verdi = it.getString("v_versjon"), id = it.getLong("bh_versjon_id")),
        søknadsformat = it.getEnum("bh_soknadsformat"),
        sisteSaksbehandler = it.getStringOrNull("bh_siste_saksbehandler")?.ifBlank { null },
        relaterteIdenter = it.getArray("rp_ident", String::class),
        relatertBehandlingId = it.getLongOrNull("b_forrige_behandling_id")?.let(::BehandlingId),
        snapShotId = it.getLong("bh_id"),
        gjeldendeAvklaringsBehov = it.getStringOrNull("bh_gjeldende_avklaringsbehov")
            ?.ifBlank { null },
        gjeldendeAvklaringsbehovStatus = it.getEnumOrNull("bh_gjeldende_avklaringsbehov_status"),
        venteÅrsak = it.getStringOrNull("bh_venteaarsak")?.ifBlank { null },
        returÅrsak = it.getStringOrNull("bh_retur_aarsak")?.ifBlank { null },
        gjeldendeStegGruppe = it.getEnumOrNull("bh_steggruppe"),
        årsaker = it.getArray("b_aarsaker_til_behandling", String::class)
            .map { Vurderingsbehov.valueOf(it) },
        resultat = it.getEnumOrNull("bh_resultat"),
        oppdatertTidspunkt = it.getLocalDateTime("bh_hendelsestidspunkt")
    )
}