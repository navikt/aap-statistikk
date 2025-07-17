package no.nav.aap.statistikk.produksjonsstyring

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Params
import no.nav.aap.statistikk.api.BehandlingerPerBehandlingstypeInputMedPeriode
import no.nav.aap.statistikk.behandling.TypeBehandling
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class BehandlingstidPerDag(val dag: LocalDate, val snitt: Double)

data class BehandlingPerSteggruppe(val steggruppe: String, val antall: Int)
data class OppgaverPerSteggruppe(val nye: Int, val lukkede: Int)

data class AntallPerDag(val dag: LocalDate, val antall: Int)

data class AntallÅpneOgTypeOgGjennomsnittsalderDTO(
    val antallÅpne: Int,
    val behandlingstype: TypeBehandling,
    val gjennomsnittsalder: Double
)

data class BøtteFordeling(val bøtte: Int, val antall: Int)

data class VenteårsakOgGjennomsnitt(
    val årsak: String,
    val antall: Int,
    val gjennomsnittligAlder: Double
)

data class BehandlingAarsakAntallGjennomsnitt(
    val årsak: String,
    val antall: Int,
    val gjennomsnittligAlder: Double
)

class ProduksjonsstyringRepository(private val connection: DBConnection) {

    fun hentBehandlingstidPerDag(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>
    ): List<BehandlingstidPerDag> {
        // TODO se på denne :)
        val sql = """
WITH oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                       FROM behandling_referanse br
                                LEFT JOIN oppgave o ON br.id = o.behandling_referanse_id
                                WHERE o.status != 'AVSLUTTET'
                       GROUP BY br.id, br.referanse),
     u as (select date_trunc('day', pbh.oppdatert_tid) dag,
                  pbh.oppdatert_tid  as                oppdatert_tid,
                  pb.mottatt_tid     as                mottatt_tid,
                  pb.type_behandling as                type_behandling,
                  e.kode             as                enhet
           from postmottak_behandling_historikk pbh
                    join postmottak_behandling pb on pb.id = pbh.postmottak_behandling_id
                    left join oppgave_enhet oe on pb.referanse = oe.referanse
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where pbh.gjeldende = true
             and pbh.status = 'AVSLUTTET'
           union all
           select date_trunc('day', bh.oppdatert_tid) dag,
                  bh.oppdatert_tid as                 oppdatert_tid,
                  bh.mottatt_tid   as                 mottatt_tid,
                  b.type           as                 type_behandling,
                  e.kode           as                 enhet
           from behandling_historikk bh
                    join behandling b on bh.behandling_id = b.id
                    join sak s on b.sak_id = s.id
                    left join oppgave_enhet oe on oe.id = b.referanse_id
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where bh.gjeldende = true
             and bh.status = 'AVSLUTTET')
select dag,
       avg(extract(epoch from (u.oppdatert_tid - u.mottatt_tid))) as snitt
from u
where (type_behandling = ANY (?::text[]) or ${'$'}1 is null)
  and (enhet = ANY (?::text[]) or ${'$'}2 is null)
group by dag
order by dag;
        """.trimIndent()


        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper { row ->
                BehandlingstidPerDag(
                    row.getLocalDate("dag"),
                    row.getDouble("snitt")
                )
            }
        }
    }

    fun antallÅpneBehandlingerOgGjennomsnittGittPeriode(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>,
        startDato: LocalDate,
        sluttDato: LocalDate
    ): List<AntallÅpneOgTypeOgGjennomsnittsalderDTO> {
        val sql = """WITH periode_grenser AS (
            SELECT ?::date AS start_dato, ?::date AS slutt_dato
        ),
        
        oppgave_enhet AS (
            SELECT br.id, o.enhet_id, br.referanse
            FROM behandling_referanse br
                     JOIN oppgave o ON br.id = o.behandling_referanse_id
            WHERE o.status != 'AVSLUTTET'
        ),
        
        u AS (
            SELECT b.type AS type,
                   current_timestamp AT TIME ZONE 'Europe/Oslo' - b.opprettet_tid AS alder,
                   e.kode AS enhet
            FROM behandling_historikk bh
                     JOIN behandling b ON b.id = bh.behandling_id
                     LEFT JOIN oppgave_enhet oe ON oe.id = b.referanse_id
                     LEFT JOIN enhet e ON e.id = oe.enhet_id
                     JOIN periode_grenser pg ON b.opprettet_tid >= pg.start_dato AND b.opprettet_tid < pg.slutt_dato
            WHERE gjeldende = true
              AND status != 'AVSLUTTET'
        
            UNION ALL
        
            SELECT pb.type_behandling AS type,
                   current_timestamp AT TIME ZONE 'Europe/Oslo' - pb.mottatt_tid AS alder,
                   e.kode AS enhet
            FROM postmottak_behandling_historikk pbh
                     JOIN postmottak_behandling pb ON pb.id = pbh.postmottak_behandling_id
                     LEFT JOIN oppgave_enhet oe ON pb.referanse = oe.referanse
                     LEFT JOIN enhet e ON e.id = oe.enhet_id
                     JOIN periode_grenser pg ON pb.mottatt_tid >= pg.start_dato AND pb.mottatt_tid < pg.slutt_dato
            WHERE gjeldende = true
              AND status != 'AVSLUTTET'
        )
        
        SELECT type,
               COUNT(*) AS count,
               EXTRACT(EPOCH FROM AVG(alder)) AS gjennomsnitt_alder
        FROM u
        WHERE (u.enhet = ANY (?::text[]) OR ${'$'}3 IS NULL)
          AND (type = ANY (?::text[]) OR ${'$'}4 IS NULL)
        GROUP BY type;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setLocalDate(1, startDato)
                setLocalDate(2, sluttDato)
                if (enheter.isEmpty()) {
                    setString(3, null)
                } else {
                    setArray(3, enheter)
                }
                setBehandlingsTyperParam(behandlingsTyper, 4)
            }
            setRowMapper { row ->
                AntallÅpneOgTypeOgGjennomsnittsalderDTO(
                    antallÅpne = row.getInt("count"),
                    behandlingstype = row.getEnum("type"),
                    gjennomsnittsalder = row.getDoubleOrNull("gjennomsnitt_alder") ?: 0.0
                )
            }
        }
    }

    fun venteÅrsakOgGjennomsnittGittPeriode(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>,
        startDato: LocalDate,
        sluttDato: LocalDate
    ): List<VenteårsakOgGjennomsnitt> {
        val sql = """WITH periode_grenser AS (
            SELECT ?::date AS start_dato, ?::date AS slutt_dato
        )
        SELECT
            venteaarsak,
            COUNT(*),
            EXTRACT(EPOCH FROM AVG(now() AT TIME ZONE 'Europe/Oslo' - behandling_historikk.oppdatert_tid)) AS avg
        FROM behandling_historikk
                 JOIN behandling b ON b.id = behandling_historikk.behandling_id
                 LEFT JOIN enhet e ON e.id = (
                     SELECT DISTINCT o.enhet_id
                     FROM oppgave o
                     WHERE o.behandling_referanse_id = b.referanse_id
                     LIMIT 1
                 )
                 JOIN periode_grenser pg ON behandling_historikk.oppdatert_tid >= pg.start_dato
                                         AND behandling_historikk.oppdatert_tid < pg.slutt_dato
        WHERE venteaarsak IS NOT NULL
          AND gjeldende = true
          AND (e.kode = ANY (?::text[]) OR ${'$'}3 IS NULL)
          AND (b.type = ANY (?::text[]) OR ${'$'}4 IS NULL)
        GROUP BY venteaarsak;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setLocalDate(1, startDato)
                setLocalDate(2, sluttDato)
                if (enheter.isEmpty()) {
                    setString(3, null)
                } else {
                    setArray(3, enheter)
                }
                setBehandlingsTyperParam(behandlingsTyper, 4)
            }
            setRowMapper {
                VenteårsakOgGjennomsnitt(
                    årsak = it.getString("venteaarsak"),
                    antall = it.getInt("count"),
                    gjennomsnittligAlder = it.getDouble("avg")
                )
            }
        }
    }

    fun antallÅpneBehandlingerOgGjennomsnitt(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>
    ): List<AntallÅpneOgTypeOgGjennomsnittsalderDTO> {
        val sql = """
WITH oppgave_enhet AS (SELECT br.id, o.enhet_id, br.referanse
                       FROM behandling_referanse br
                                JOIN oppgave o ON br.id = o.behandling_referanse_id
                       WHERE o.status != 'AVSLUTTET'),

     u as (select b.type                                                         as type,
                  current_timestamp at time zone 'Europe/Oslo' - b.opprettet_tid as alder,
                  e.kode                                                         as enhet
           from behandling_historikk bh
                    join behandling b on b.id = bh.behandling_id
                    left join oppgave_enhet oe on oe.id = b.referanse_id
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where gjeldende = true
             and status != 'AVSLUTTET'
           union all
           select pb.type_behandling                                            as type,
                  current_timestamp at time zone 'Europe/Oslo' - pb.mottatt_tid as alder,
                  e.kode                                                        as enhet
           from postmottak_behandling_historikk pbh
                    join postmottak_behandling pb on pb.id = pbh.postmottak_behandling_id
                    left join oppgave_enhet oe on pb.referanse = oe.referanse
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where gjeldende = true
             and status != 'AVSLUTTET')
select type,
       count(*),
       extract(epoch from
               avg(alder)) as gjennomsnitt_alder
from u
where (type = ANY (?::text[]) or ${'$'}1 is null)
  and (u.enhet = ANY (?::text[]) or ${'$'}2 is null)
group by type;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper { row ->
                AntallÅpneOgTypeOgGjennomsnittsalderDTO(
                    antallÅpne = row.getInt("count"),
                    behandlingstype = row.getEnum("type"),
                    gjennomsnittsalder = row.getDoubleOrNull("gjennomsnitt_alder") ?: 0.0
                )
            }
        }
    }

    fun antallBehandlingerPerSteggruppe(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>,
        oppgaveTyper: List<String>
    ): List<BehandlingPerSteggruppe> {
        // TODO! Trenger steggruppe fra postmottak først
        val sql = """
WITH oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                       FROM behandling_referanse br
                                LEFT JOIN oppgave o ON br.id = o.behandling_referanse_id
                       WHERE o.status != 'AVSLUTTET'
                       GROUP BY br.id, br.referanse),
     u as (select gjeldende_avklaringsbehov, e.kode as enhet, b.type as type_behandling
           from behandling_historikk
                    join behandling b on b.id = behandling_historikk.behandling_id
                    left join oppgave_enhet oe on b.referanse_id = oe.id
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where gjeldende_avklaringsbehov is not null
             and gjeldende = true
           union all
           select gjeldende_avklaringsbehov, e.kode as enhet, pb.type_behandling as type_behandling
           from postmottak_behandling_historikk pbh
                    left join postmottak_behandling pb on pb.id = pbh.postmottak_behandling_id
                    left join oppgave_enhet oe on pb.referanse = oe.referanse
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where gjeldende_avklaringsbehov is not null
             and gjeldende = true)
select gjeldende_avklaringsbehov, count(*)
from u
where (type_behandling = ANY (?::text[]) or ${'$'}1 is null)
  and (enhet = ANY (?::text[]) or ${'$'}2 is null)
  and (gjeldende_avklaringsbehov = ANY (?::text[]) or ${'$'}3 is null)
group by gjeldende_avklaringsbehov;

        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
                if (oppgaveTyper.isEmpty()) {
                    setString(3, null)
                } else {
                    setArray(3, oppgaveTyper)
                }
            }
            setRowMapper { row ->
                BehandlingPerSteggruppe(
                    steggruppe = tilSteggruppe(row.getString("gjeldende_avklaringsbehov")),
                    antall = row.getInt("count")
                )
            }
        }.groupBy { it.steggruppe }
            .map { BehandlingPerSteggruppe(it.key, it.value.sumOf { it.antall }) }
    }

    fun antallÅpneOppgaver(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>,
        startDato: LocalDate,
        sluttDato: LocalDate,
        oppgaveTyper: List<String>
    ): Int {
        val sql = """
            WITH oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                                   FROM behandling_referanse br
                                            LEFT JOIN oppgave o ON br.id = o.behandling_referanse_id
                                   WHERE o.status != 'AVSLUTTET'
                                   AND o.opprettet_tidspunkt >= ?::date
                                   AND o.opprettet_tidspunkt <= ?::date
                                   GROUP BY br.id, br.referanse),
                 u as (select gjeldende_avklaringsbehov, e.kode as enhet, b.type as type_behandling
                       from behandling_historikk
                                join behandling b on b.id = behandling_historikk.behandling_id
                                join oppgave_enhet oe on b.referanse_id = oe.id
                                LEFT JOIN enhet e ON e.id = oe.enhet_id
                       where gjeldende_avklaringsbehov is not null
                         and gjeldende = true
                       union all
                       select gjeldende_avklaringsbehov, e.kode as enhet, pb.type_behandling as type_behandling
                       from postmottak_behandling_historikk pbh
                                left join postmottak_behandling pb on pb.id = pbh.postmottak_behandling_id
                                join oppgave_enhet oe on pb.referanse = oe.referanse
                                LEFT JOIN enhet e ON e.id = oe.enhet_id
                       where gjeldende_avklaringsbehov is not null
                         and gjeldende = true)
            select gjeldende_avklaringsbehov, count(*)
            from u
            where (type_behandling = ANY (?::text[]) or ${'$'}3 is null)
              and (enhet = ANY (?::text[]) or ${'$'}4 is null)
              and (gjeldende_avklaringsbehov = ANY (?::text[]) or ${'$'}5 is null)
            group by gjeldende_avklaringsbehov;
        """.trimIndent()

        return connection.queryFirst(sql) {
            setParams {
                setLocalDate(1, startDato)
                setLocalDate(2, sluttDato)
                setBehandlingsTyperParam(behandlingsTyper, 3)
                if (enheter.isEmpty()) {
                    setString(4, null)
                } else {
                    setArray(4, enheter)
                }
                if (oppgaveTyper.isEmpty()) {
                    setString(5, null)
                } else {
                    setArray(5, oppgaveTyper)
                }
            }
            setRowMapper { row -> row.getInt("antall") }
        }
    }

    fun antallLukkedeOppgaver(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>,
        startDato: LocalDate,
        sluttDato: LocalDate,
        oppgaveTyper: List<String>
    ): Int {
        val sql = """
            WITH oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                                   FROM behandling_referanse br
                                            LEFT JOIN oppgave o ON br.id = o.behandling_referanse_id
                                   WHERE o.status = 'AVSLUTTET'
                                   AND o.opprettet_tidspunkt >= ?::date
                                   AND o.opprettet_tidspunkt <= ?::date
                                   GROUP BY br.id, br.referanse),
                 u as (select gjeldende_avklaringsbehov, e.kode as enhet, b.type as type_behandling
                       from behandling_historikk
                                join behandling b on b.id = behandling_historikk.behandling_id
                                join oppgave_enhet oe on b.referanse_id = oe.id
                                LEFT JOIN enhet e ON e.id = oe.enhet_id
                       where gjeldende_avklaringsbehov is not null
                         and gjeldende = true
                       union all
                       select gjeldende_avklaringsbehov, e.kode as enhet, pb.type_behandling as type_behandling
                       from postmottak_behandling_historikk pbh
                                left join postmottak_behandling pb on pb.id = pbh.postmottak_behandling_id
                                join oppgave_enhet oe on pb.referanse = oe.referanse
                                LEFT JOIN enhet e ON e.id = oe.enhet_id
                       where gjeldende_avklaringsbehov is not null
                         and gjeldende = true)
            select gjeldende_avklaringsbehov, count(*)
            from u
            where (type_behandling = ANY (?::text[]) or ${'$'}3 is null)
              and (enhet = ANY (?::text[]) or ${'$'}4 is null)
              and (gjeldende_avklaringsbehov = ANY (?::text[]) or ${'$'}5 is null)
            group by gjeldende_avklaringsbehov;
        """.trimIndent()

        return connection.queryFirst(sql) {
            setParams {
                setLocalDate(1, startDato)
                setLocalDate(2, sluttDato)
                setBehandlingsTyperParam(behandlingsTyper, 3)
                if (enheter.isEmpty()) {
                    setString(4, null)
                } else {
                    setArray(4, enheter)
                }
                if (oppgaveTyper.isEmpty()) {
                    setString(5, null)
                } else {
                    setArray(5, oppgaveTyper)
                }
            }
            setRowMapper { row -> row.getInt("antall") }
        }
    }

    fun opprettedeBehandlingerPerDag(
        antallDager: Int = 7,
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>
    ): List<AntallPerDag> {
        val sql = """
WITH filtered_behandling AS (SELECT b.id, b.opprettet_tid, b.referanse_id, b.type
                             FROM behandling b
                             WHERE b.opprettet_tid >
                                   current_date AT TIME ZONE 'Europe/Oslo' - interval '$antallDager days'),
     oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                       FROM behandling_referanse br
                                JOIN oppgave o ON br.id = o.behandling_referanse_id
                       WHERE o.status != 'AVSLUTTET'
                       GROUP BY br.id, br.referanse)
SELECT date(fb.opprettet_tid) AS dag, COUNT(*) AS antall
FROM filtered_behandling fb
         JOIN behandling_historikk bh ON fb.id = bh.behandling_id
         LEFT JOIN oppgave_enhet oe ON fb.referanse_id = oe.id
         LEFT JOIN enhet e ON e.id = oe.enhet_id
WHERE bh.gjeldende = true
  AND (fb.type = ANY (?::text[]) OR ${'$'}1 IS NULL)
  AND (e.kode = ANY (?::text[]) OR ${'$'}2 IS NULL)
GROUP BY dag
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper {
                AntallPerDag(it.getLocalDate("dag"), it.getInt("antall"))
            }
        }

    }

    fun antallAvsluttedeBehandlingerPerDag(
        antallDager: Int = 7,
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>
    ): List<AntallPerDag> {
        // todo
        val sql = """
WITH filtered_behandling AS (SELECT b.id, bh.oppdatert_tid, b.referanse_id, b.type
                             FROM behandling b
                                      join behandling_historikk bh on b.id = bh.behandling_id
                             WHERE bh.oppdatert_tid >
                                   current_date AT TIME ZONE 'Europe/Oslo' - interval '$antallDager days'
                               and gjeldende = true
                               and bh.status = 'AVSLUTTET'),
     oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                       FROM behandling_referanse br
                                   JOIN oppgave o ON br.id = o.behandling_referanse_id
                       WHERE o.status != 'AVSLUTTET'
                       GROUP BY br.id, br.referanse)
SELECT date(fb.oppdatert_tid) AS dag, COUNT(*) AS antall
FROM filtered_behandling fb
         LEFT JOIN oppgave_enhet oe ON fb.referanse_id = oe.id
         LEFT JOIN enhet e ON e.id = oe.enhet_id
    WHERE (fb.type = ANY (?::text[]) OR ${'$'}1 IS NULL)
    AND (e.kode = ANY (?::text[]) OR ${'$'}2 IS NULL)
GROUP BY dag
ORDER BY dag;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper {
                AntallPerDag(it.getLocalDate("dag"), it.getInt("antall"))
            }
        }
    }

    fun antallÅpneBehandlinger(behandlingsTyper: List<TypeBehandling>, enheter: List<String>): Int {
        val sql = """            
WITH filtered_behandling AS (SELECT b.id, b.referanse_id, b.type
                             FROM behandling b
                                      join behandling_historikk bh on b.id = bh.behandling_id
                                 and gjeldende = true
                                 and bh.status != 'AVSLUTTET'),
     oppgave_enhet AS (SELECT br.id, MIN(o.enhet_id) AS enhet_id, br.referanse
                       FROM behandling_referanse br
                                JOIN oppgave o ON br.id = o.behandling_referanse_id
                       WHERE o.status != 'AVSLUTTET'
                       GROUP BY br.id, br.referanse)
SELECT count(fb.id) antall
FROM filtered_behandling fb
         LEFT JOIN oppgave_enhet oe ON fb.referanse_id = oe.id
         LEFT JOIN enhet e ON e.id = oe.enhet_id
    AND (fb.type = ANY (?::text[]) OR ${'$'}1 IS NULL)
    AND (e.kode = ANY (?::text[]) OR ${'$'}2 IS NULL);
        """.trimIndent()

        return connection.queryFirst(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper { it.getInt("antall") }
        }
    }

    fun alderÅpneBehandlinger(
        bøttestørrelse: Int = 1,
        enhet: ChronoUnit = ChronoUnit.DAYS,
        antallBøtter: Int = 30,
        behandlingsTyper: List<TypeBehandling> = emptyList(),
        enheter: List<String> = emptyList(),
        alderFra: LocalDateTime = LocalDateTime.now(),
    ): List<BøtteFordeling> {
        val totaltSekunder = enhet.duration.seconds * bøttestørrelse * antallBøtter
        val sql = """
  WITH oppgave_enhet AS (SELECT br.id, o.enhet_id, br.referanse
                         FROM behandling_referanse br
                                  JOIN oppgave o ON br.id = o.behandling_referanse_id
                         WHERE o.status != 'AVSLUTTET'),
       u AS (SELECT pb.mottatt_tid     AS mottatt_tid,
                    pb.type_behandling AS type,
                    e.kode             AS enhet
             FROM postmottak_behandling_historikk pbh
                      JOIN postmottak_behandling pb
                           ON pbh.postmottak_behandling_id = pb.id
                      left join oppgave_enhet oe on pb.referanse = oe.referanse
                      LEFT JOIN enhet e ON e.id = oe.enhet_id
             WHERE pbh.status != 'AVSLUTTET'
               AND gjeldende = true
             UNION ALL
             SELECT bh.mottatt_tid AS mottatt_tid,
                    b.type         AS type,
                    e.kode         AS enhet
             FROM behandling_historikk bh
                      JOIN behandling b ON b.id = bh.behandling_id
                      left join oppgave_enhet oe on oe.id = b.referanse_id
                      LEFT JOIN enhet e ON e.id = oe.enhet_id
             WHERE b.id = bh.behandling_id
               AND bh.gjeldende = true
               AND bh.status != 'AVSLUTTET')
  select width_bucket(EXTRACT(EPOCH FROM
                              (?::timestamp at time zone 'Europe/Oslo' - mottatt_tid)), 0,
                      $totaltSekunder, $antallBøtter) as bucket,
         count(*)
  from u
  where (type = ANY (?::text[])
      or ${'$'}2 is null)
    and (enhet = ANY (?::text[])
      or ${'$'}3 is null)
  
  GROUP BY bucket
  ORDER BY bucket;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setLocalDateTime(1, alderFra)
                setBehandlingsTyperParam(behandlingsTyper, 2)
                if (enheter.isEmpty()) {
                    setString(3, null)
                } else {
                    setArray(3, enheter)
                }
            }
            setRowMapper {
                BøtteFordeling(it.getInt("bucket"), it.getInt("count"))
            }
        }
    }

    fun alderLukkedeBehandlinger(
        bøttestørrelse: Int = 1,
        enhet: ChronoUnit = ChronoUnit.DAYS,
        antallBøtter: Int = 30,
        behandlingsTyper: List<TypeBehandling> = emptyList(),
        enheter: List<String>,
        alderFra: LocalDateTime = LocalDateTime.now(),
    ): List<BøtteFordeling> {
        val totaltSekunder = enhet.duration.seconds * bøttestørrelse * antallBøtter
        val sql = """
WITH oppgave_enhet AS (SELECT br.id, o.enhet_id, br.referanse
                       FROM behandling_referanse br
                                JOIN oppgave o ON br.id = o.behandling_referanse_id
                       WHERE o.status != 'AVSLUTTET'),
     u as (select pb.mottatt_tid     as mottatt_tid,
                  pb.type_behandling as type,
                  e.kode             as enhet
           from postmottak_behandling_historikk pbh
                    join postmottak_behandling pb
                         on pbh.postmottak_behandling_id = pb.id
                    left join oppgave_enhet oe on pb.referanse = oe.referanse
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where pbh.status = 'AVSLUTTET'
             and gjeldende = true
           union all
           select bh.mottatt_tid as mottatt_tid,
                  b.type         as type,
                  e.kode         as enhet
           from behandling_historikk bh
                    join behandling b on b.id = bh.behandling_id
                    left join oppgave_enhet oe on oe.id = b.referanse_id
                    LEFT JOIN enhet e ON e.id = oe.enhet_id
           where b.id = bh.behandling_id
             and bh.gjeldende = true
             and bh.status = 'AVSLUTTET')
select width_bucket(EXTRACT(EPOCH FROM
                            (?::timestamp at time zone 'Europe/Oslo' - mottatt_tid)), 0,
                    $totaltSekunder, $antallBøtter) as bucket,
       count(*)
from u
where (type = ANY (?::text[]) or ${'$'}2 is null)
  and (enhet = ANY (?::text[]) or ${'$'}3 is null)
group by bucket
order by bucket;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setLocalDateTime(1, alderFra)
                setBehandlingsTyperParam(behandlingsTyper, index = 2)
                if (enheter.isEmpty()) {
                    setString(3, null)
                } else {
                    setArray(3, enheter)
                }
            }
            setRowMapper {
                BøtteFordeling(it.getInt("bucket"), it.getInt("count"))
            }
        }
    }

    private fun Params.setBehandlingsTyperParam(
        behandlingsTyper: List<TypeBehandling>,
        index: Int = 1
    ) {
        if (behandlingsTyper.isEmpty()) {
            setString(index, null)
        } else {
            setArray(index, behandlingsTyper.map { it.toString() })
        }
    }

    fun venteÅrsakOgGjennomsnitt(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>
    ): List<VenteårsakOgGjennomsnitt> {
        val sql = """
            select venteaarsak,
                   count(*),
                   extract(epoch from
                           avg(now() at time zone 'Europe/Oslo' - behandling_historikk.oppdatert_tid)) as avg
            from behandling_historikk
                     join behandling b on b.id = behandling_historikk.behandling_id
                     LEFT JOIN enhet e ON e.id = (SELECT distinct o.enhet_id
                                                  FROM oppgave o
                                                  WHERE o.behandling_referanse_id = b.referanse_id
                                                  LIMIT 1)
            where venteaarsak IS NOT NULL
              and gjeldende = true
              and (b.type = ANY (?::text[]) or $1 is null)
              and (e.kode = ANY (?::text[]) or ${'$'}2 is null)
            group by venteaarsak;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper {
                VenteårsakOgGjennomsnitt(
                    årsak = it.getString("venteaarsak"),
                    antall = it.getInt("count"),
                    gjennomsnittligAlder = it.getDouble("avg")
                )
            }
        }
    }

    fun antallBehandlingerPerÅrsak(
        behandlingsTyper: List<TypeBehandling>,
        enheter: List<String>
    ): List<BehandlingAarsakAntallGjennomsnitt> {
        val sql = """
select unnest(b.aarsaker_til_behandling)                                       as aarsak,
       count(*),
       extract(epoch from avg(current_timestamp at time zone 'Europe/Oslo' -
                                                             b.opprettet_tid)) as avg_alder
from behandling b
         join behandling_historikk bh on b.id = bh.behandling_id
         LEFT JOIN enhet e ON e.id = (SELECT distinct o.enhet_id
                                      FROM oppgave o
                                      WHERE o.behandling_referanse_id = b.referanse_id
                                        AND o.status != 'AVSLUTTET'
                                      LIMIT 1)
where (b.type = ANY (?::text[]) or ${'$'}1 is null)
  and bh.gjeldende = true
  and (e.kode = ANY (?::text[]) or ${'$'}2 is null)
  and bh.status != 'AVSLUTTET'
group by aarsak;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
                if (enheter.isEmpty()) {
                    setString(2, null)
                } else {
                    setArray(2, enheter)
                }
            }
            setRowMapper {
                BehandlingAarsakAntallGjennomsnitt(
                    årsak = it.getString("aarsak"),
                    antall = it.getInt("count"),
                    gjennomsnittligAlder = it.getDouble("avg_alder")
                )
            }
        }
    }

}

/**
 * Stygg måte å hente ut steggruppe uavhengig av hvor definisjon kommer fra.
 *
 * Burde nok kode om til en "union"-definisjon innad i kodebasen et sted.
 */
fun tilSteggruppe(kode: String): String {
    return try {
        Definisjon.forKode(kode).løsesISteg.gruppe.toString()
    } catch (e: IllegalArgumentException) {
        no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.forKode(kode).løsesISteg.gruppe.toString()
    } catch (e: NoSuchElementException) {
        throw IllegalStateException("Finner ikke for kode $kode")
    }
}
