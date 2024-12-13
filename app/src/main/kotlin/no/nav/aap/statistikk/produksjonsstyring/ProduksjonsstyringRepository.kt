package no.nav.aap.statistikk.produksjonsstyring

import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Params
import no.nav.aap.statistikk.behandling.TypeBehandling
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BehandlingstidPerDag(val dag: LocalDate, val snitt: Double)

data class BehandlingPerAvklaringsbehov(val antall: Int, val behov: String)

data class BehandlingPerSteggruppe(val steggruppe: StegGruppe, val antall: Int)

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

    fun hentBehandlingstidPerDag(behandlingsTyper: List<TypeBehandling>): List<BehandlingstidPerDag> {
        val sql = """
            select date_trunc('day', tom) dag, avg(EXTRACT(EPOCH FROM (tom - fom))) as snitt
            from (select bh.mottatt_tid   fom,
                         bh.oppdatert_tid tom
                  from sak s,
                       behandling b,
                       behandling_historikk bh
                  where s.id = b.sak_id
                    and b.id = bh.behandling_id
                    and bh.gjeldende = true 
                    and (b.type = ANY(?::text[]) or ${'$'}1 is null)
                    and bh.status = 'AVSLUTTET') fomtom
            group by dag
            order by dag
        """.trimIndent()


        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper { row ->
                BehandlingstidPerDag(
                    row.getLocalDate("dag"),
                    row.getDouble("snitt")
                )
            }
        }
    }

    fun antallÅpneBehandlingerOgGjennomsnitt(): List<AntallÅpneOgTypeOgGjennomsnittsalderDTO> {
        val sql = """
            select type,
                   count(*),
                   extract(epoch from
                           avg(current_timestamp at time zone 'Europe/Oslo' - b.opprettet_tid)) as gjennomsnitt_alder
            from behandling_historikk bh
                     join public.behandling b on b.id = bh.behandling_id
            where gjeldende = true
              and status != 'AVSLUTTET'
            group by b.type
        """.trimIndent()

        return connection.queryList(sql) {
            setRowMapper { row ->
                AntallÅpneOgTypeOgGjennomsnittsalderDTO(
                    antallÅpne = row.getInt("count"),
                    behandlingstype = row.getEnum("type"),
                    gjennomsnittsalder = row.getDoubleOrNull("gjennomsnitt_alder") ?: 0.0
                )
            }
        }
    }

    fun antallÅpneBehandlingerPerAvklaringsbehov(behandlingsTyper: List<TypeBehandling>): List<BehandlingPerAvklaringsbehov> {
        val sql = """
            select count(*), gjeldende_avklaringsbehov
            from behandling_historikk
                     join behandling b on b.id = behandling_historikk.behandling_id
            where gjeldende = true
              and status != 'AVSLUTTET'
              and (b.type = ANY (?::text[]) or ${'$'}1 is null)
            group by gjeldende_avklaringsbehov;
        """.trimIndent()


        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper { row ->
                BehandlingPerAvklaringsbehov(
                    antall = row.getInt("count"),
                    behov = row.getStringOrNull("gjeldende_avklaringsbehov") ?: "UKJENT"
                )
            }
        }
    }

    fun antallBehandlingerPerSteggruppe(behandlingsTyper: List<TypeBehandling>): List<BehandlingPerSteggruppe> {
        val sql = """
            select steggruppe, count(*)
            from behandling_historikk
                     join behandling b on b.id = behandling_historikk.behandling_id
            where steggruppe is not null
              and gjeldende = true
              and (b.type = ANY (?::text[]) or ${'$'}1 is null)
            group by steggruppe;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper { row ->
                BehandlingPerSteggruppe(
                    steggruppe = row.getEnum("steggruppe"),
                    antall = row.getInt("count")
                )
            }
        }
    }

    fun antallNyeBehandlingerPerDag(
        antallDager: Int = 7,
        behandlingsTyper: List<TypeBehandling>
    ): List<AntallPerDag> {
        val sql = """
            select date(b.opprettet_tid) as dag,
                   count(*)                 antall
            from behandling b
            where b.opprettet_tid > current_date at time zone 'Europe/Oslo' - interval '$antallDager days'
              and (b.type = ANY (?::text[]) or ${'$'}1 is null)
            group by dag
            order by dag
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper {
                AntallPerDag(it.getLocalDate("dag"), it.getInt("antall"))
            }
        }

    }

    fun antallAvsluttedeBehandlingerPerDag(
        antallDager: Int = 7,
        behandlingsTyper: List<TypeBehandling>
    ): List<AntallPerDag> {
        val sql = """
            select date(bh.oppdatert_tid) as dag,
                   count(*)                  antall
            from behandling b,
                 behandling_historikk bh
            where b.id = bh.behandling_id
              and bh.gjeldende = true
              and (b.type = ANY (?::text[]) or ${'$'}1 is null)
              and bh.status = 'AVSLUTTET'
              and bh.oppdatert_tid > current_date at time zone 'Europe/Oslo' - interval '$antallDager days'
            group by dag
            order by dag
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper {
                AntallPerDag(it.getLocalDate("dag"), it.getInt("antall"))
            }
        }
    }

    fun alderPåFerdigeBehandlingerSisteDager(
        antallDager: Int,
        behandlingsTyper: List<TypeBehandling>
    ): Double {
        val sql = """
            select avg(extract(epoch from bh.oppdatert_tid - bh.mottatt_tid))
            from behandling_historikk bh
                     join behandling b on b.id = bh.behandling_id
            where status = 'AVSLUTTET'
              and (b.type = ANY (?::text[]) or ${'$'}1 is null)
              and bh.oppdatert_tid > current_date - interval '$antallDager days';
        """.trimIndent()
        return connection.queryFirst(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper {
                it.getDoubleOrNull("avg") ?: 0.0
            }
        }
    }

    fun antallÅpneBehandlinger(behandlingsTyper: List<TypeBehandling>): Int {
        val sql = """            
            select count(b.id) antall
            from behandling b,
                 behandling_historikk bh
            where b.id = bh.behandling_id
              and bh.gjeldende = true
              and (b.type = ANY (?::text[]) or ${'$'}1 is null)
              and bh.status != 'AVSLUTTET';
        """.trimIndent()

        return connection.queryFirst(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper { it.getInt("antall") }
        }

    }

    fun alderÅpneBehandlinger(
        bøttestørrelse: Int = 1,
        enhet: ChronoUnit = ChronoUnit.DAYS,
        antallBøtter: Int = 30,
        behandlingsTyper: List<TypeBehandling> = emptyList()
    ): List<BøtteFordeling> {
        val totaltSekunder = enhet.duration.seconds * bøttestørrelse * antallBøtter
        val sql = """
            with dt as (select bh.behandling_id                                                       bid,
                               EXTRACT(EPOCH FROM
                                       (current_date at time zone 'Europe/Oslo' - bh.mottatt_tid)) as diff
                        from sak s,
                             behandling b,
                             behandling_historikk bh
                        where s.id = b.sak_id
                          and b.id = bh.behandling_id
                          and bh.gjeldende = true
                          and (b.type = ANY (?::text[]) or $1 is null)
                          and bh.status != 'AVSLUTTET')
            select width_bucket(diff, 0, $totaltSekunder, $antallBøtter) as bucket, count(*)
            from dt
            group by bucket;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
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
        behandlingsTyper: List<TypeBehandling> = emptyList()
    ): List<BøtteFordeling> {
        val totaltSekunder = enhet.duration.seconds * bøttestørrelse * antallBøtter
        val sql = """
            with dt as (select bh.behandling_id                                           bid,
                               EXTRACT(EPOCH FROM (bh.oppdatert_tid - bh.mottatt_tid)) as diff
                        from sak s,
                             behandling b,
                             behandling_historikk bh
                        where s.id = b.sak_id
                          and b.id = bh.behandling_id
                          and bh.gjeldende = true
                          and (b.type = ANY (?::text[]) or $1 is null)
                          and bh.status = 'AVSLUTTET')
            select width_bucket(diff, 0, $totaltSekunder, $antallBøtter) as bucket, count(*)
            from dt
            group by bucket;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
            }
            setRowMapper {
                BøtteFordeling(it.getInt("bucket"), it.getInt("count"))
            }
        }
    }

    private fun Params.setBehandlingsTyperParam(behandlingsTyper: List<TypeBehandling>) {
        if (behandlingsTyper.isEmpty()) {
            setString(1, null)
        } else {
            setArray(1, behandlingsTyper.map { it.toString() })
        }
    }

    fun venteÅrsakOgGjennomsnitt(behandlingsTyper: List<TypeBehandling>): List<VenteårsakOgGjennomsnitt> {
        val sql = """
            select venteaarsak,
                   count(*),
                   extract(epoch from
                           avg(now() at time zone 'Europe/Oslo' - behandling_historikk.oppdatert_tid)) as avg
            from behandling_historikk
                     join behandling b on b.id = behandling_historikk.behandling_id
            where venteaarsak IS NOT NULL
              and gjeldende = true
              and (b.type = ANY (?::text[]) or $1 is null)
            group by venteaarsak;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
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

    fun antallBehandlingerPerÅrsak(behandlingsTyper: List<TypeBehandling>): List<BehandlingAarsakAntallGjennomsnitt> {
        val sql = """
select unnest(b.aarsaker_til_behandling)                                       as aarsak,
       count(*),
       extract(epoch from avg(current_timestamp at time zone 'Europe/Oslo' -
                                                             b.opprettet_tid)) as avg_alder
from behandling b
         join behandling_historikk bh on b.id = bh.behandling_id
where (b.type = ANY (?::text[]) or ${'$'}1 is null)
  and  bh.gjeldende = true
  and bh.status != 'AVSLUTTET'
group by aarsak;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                setBehandlingsTyperParam(behandlingsTyper)
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

