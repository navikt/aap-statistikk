package no.nav.aap.statistikk.produksjonsstyring

import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.behandling.TypeBehandling
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BehandlingstidPerDag(val dag: LocalDate, val snitt: Double)

data class BehandlingPerAvklaringsbehov(val antall: Int, val behov: String)

data class BehandlingPerSteggruppe(val steggruppe: StegGruppe, val antall: Int)

data class AntallPerDag(val dag: LocalDate, val antall: Int)

data class AntallÅpneOgGjennomsnitt(val antallÅpne: Int, val gjennomsnittsalder: Double)

data class BøtteFordeling(val bøtte: Int, val antall: Int)

data class VenteårsakOgGjennomsnitt(
    val årsak: String,
    val antall: Int,
    val gjennomsnittligAlder: Double
)

class ProduksjonsstyringRepository(private val connection: DBConnection) {

    fun hentBehandlingstidPerDag(typeBehandling: TypeBehandling? = null): List<BehandlingstidPerDag> {
        val sql = """
            select date_trunc('day', tom) dag, avg(EXTRACT(EPOCH FROM (tom - fom))) as snitt
            from (select bh.mottatt_tid   fom,
                         bh.oppdatert_tid tom
                  from sak s,
                       behandling b,
                       behandling_historikk bh
                  where s.id = b.sak_id
                    and b.id = bh.behandling_id
                    and bh.gjeldende = true ${typeBehandlingClaus(typeBehandling)}
                    and bh.status = 'AVSLUTTET') fomtom
            group by dag
            order by dag
        """.trimIndent()


        return connection.queryList(sql) {
            setParams {
                var count = 1
                if (typeBehandling != null) {
                    setString(count++, typeBehandling.name)
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

    fun antallÅpneBehandlingerOgGjennomsnitt(behandlingsTyper: List<TypeBehandling>): AntallÅpneOgGjennomsnitt {
        val sql = """
            select count(*),
                   extract(epoch from
                           avg(current_timestamp at time zone 'Europe/Oslo' - b.opprettet_tid)) as gjennomsnitt_alder
            from behandling_historikk bh
                     join public.behandling b on b.id = bh.behandling_id
            where gjeldende = true
              and (b.type = ANY(?::text[]) or $1 is null)
              and status != 'AVSLUTTET' 
        """.trimIndent()

        return connection.queryFirst(sql) {
            setParams {
                if (behandlingsTyper.isNotEmpty()) {
                    setArray(1, behandlingsTyper.map { it.toString() })
                } else {
                    // string fordi setArray ikke støtter null
                    setString(1, null)
                }
            }
            setRowMapper { row ->
                AntallÅpneOgGjennomsnitt(
                    antallÅpne = row.getInt("count"),
                    gjennomsnittsalder = row.getDoubleOrNull("gjennomsnitt_alder") ?: 0.0
                )
            }
        }
    }

    fun antallÅpneBehandlingerPerAvklaringsbehov(): List<BehandlingPerAvklaringsbehov> {
        val sql = """
            select count(*), gjeldende_avklaringsbehov
            from behandling_historikk
            where gjeldende = true
              and status != 'AVSLUTTET'
            group by gjeldende_avklaringsbehov;
        """.trimIndent()


        return connection.queryList(sql) {
            setRowMapper { row ->
                BehandlingPerAvklaringsbehov(
                    antall = row.getInt("count"),
                    behov = row.getStringOrNull("gjeldende_avklaringsbehov") ?: "UKJENT"
                )
            }
        }
    }

    fun antallBehandlingerPerSteggruppe(): List<BehandlingPerSteggruppe> {
        val sql = """
            select steggruppe, count(*)
            from behandling_historikk
            where steggruppe is not null and gjeldende = true
            group by steggruppe;
        """.trimIndent()

        return connection.queryList(sql) {
            setRowMapper { row ->
                BehandlingPerSteggruppe(
                    steggruppe = row.getEnum("steggruppe"),
                    antall = row.getInt("count")
                )
            }
        }
    }

    fun antallNyeBehandlingerPerDag(antallDager: Int = 7): List<AntallPerDag> {
        val sql = """
            select
                date(b.opprettet_tid) as dag,
                count(*) antall
            from
                behandling b
            where              
                b.opprettet_tid > current_date at time zone 'Europe/Oslo' - interval '$antallDager days'
            group by dag
            order by dag
        """.trimIndent()

        return connection.queryList(sql) {
            setRowMapper {
                AntallPerDag(it.getLocalDate("dag"), it.getInt("antall"))
            }
        }

    }

    fun antallAvsluttedeBehandlingerPerDag(antallDager: Int = 7): List<AntallPerDag> {
        val sql = """
            select
                date(bh.oppdatert_tid) as dag,
                count(*) antall
            from
                behandling b,
                behandling_historikk bh
            where
                b.id = bh.behandling_id and
                bh.gjeldende = true and
                bh.status = 'AVSLUTTET' and
                bh.oppdatert_tid > current_date at time zone 'Europe/Oslo' - interval '$antallDager days'
            group by dag
            order by dag
        """.trimIndent()

        return connection.queryList(sql) {
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
            where status = 'AVSLUTTET'
              and and (b.type = ANY(?::text[]) or ${'$'}1 is null)
              and bh.oppdatert_tid > current_date - interval '$antallDager days';

        """.trimIndent()

        return connection.queryFirst(sql) {
            setParams {
                if (behandlingsTyper.isEmpty()) {
                    setString(1, null)
                } else {
                    setArray(1, behandlingsTyper.map { it.toString() })
                }
            }
            setRowMapper {
                it.getDouble("avg")
            }
        }
    }

    fun antallÅpneBehandlinger(): Int {
        val sql = """            
            select
                count(b.id) antall
            from
                behandling b,
                behandling_historikk bh
            where
                b.id = bh.behandling_id and
                bh.gjeldende = true and
                bh.status != 'AVSLUTTET';
        """.trimIndent()

        return connection.queryFirst(sql) {
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
                          and (b.type = ANY (?::text[]) or ${'$'}1 is null)
                          and bh.status != 'AVSLUTTET')
            select width_bucket(diff, 0, $totaltSekunder, $antallBøtter) as bucket, count(*)
            from dt
            group by bucket;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                if (behandlingsTyper.isEmpty()) {
                    setString(1, null)
                } else {
                    setArray(1, behandlingsTyper.map { it.toString() })
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
                          and (b.type = ANY (?::text[]) or ${'$'}1 is null)
                          and bh.status = 'AVSLUTTET')
            select width_bucket(diff, 0, $totaltSekunder, $antallBøtter) as bucket, count(*)
            from dt
            group by bucket;
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                if (behandlingsTyper.isEmpty()) {
                    setString(1, null)
                } else {
                    setArray(1, behandlingsTyper.map { it.toString() })
                }
            }
            setRowMapper {
                BøtteFordeling(it.getInt("bucket"), it.getInt("count"))
            }
        }
    }

    fun venteÅrsakOgGjennomsnitt(): List<VenteårsakOgGjennomsnitt> {
        val sql = """
            select venteaarsak,
                   count(*),
                   extract(epoch from
                           avg(now() at time zone 'Europe/Oslo' - behandling_historikk.oppdatert_tid)) as avg
            from behandling_historikk
            where venteaarsak IS NOT NULL
              and gjeldende = true
            group by venteaarsak;
        """.trimIndent()

        return connection.queryList(sql) {
            setRowMapper {
                VenteårsakOgGjennomsnitt(
                    årsak = it.getString("venteaarsak"),
                    antall = it.getInt("count"),
                    gjennomsnittligAlder = it.getDouble("avg")
                )
            }
        }
    }

    private fun typeBehandlingClaus(typeBehandling: TypeBehandling?): String {
        if (typeBehandling == null) return ""
        return "and b.type = ?"
    }
}

