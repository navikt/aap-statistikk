package no.nav.aap.statistikk.produksjonsstyring

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import java.time.LocalDate

data class BehandlingstidPerDag(val dag: LocalDate, val snitt: Double)

data class BehandlingPerAvklaringsbehov(val antall: Int, val behov: String)

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
                    and bh.gjeldende = true
                    ${typeBehandlingClaus(typeBehandling)}
                    and bh.status = 'AVSLUTTET')
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

    fun antallÅpneBehandlinger(): Int {
        val sql = """
            select count(*) from behandling_historikk where gjeldende = true and status != 'AVSLUTTET'
        """.trimIndent()

        return connection.queryFirst(sql) {
            setRowMapper { row ->
                row.getInt("count")
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

    private fun typeBehandlingClaus(typeBehandling: TypeBehandling?): String {
        if (typeBehandling == null) return ""
        return "and b.type = ?"
    }
}

