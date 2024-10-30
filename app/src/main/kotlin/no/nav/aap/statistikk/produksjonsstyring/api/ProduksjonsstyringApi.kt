package no.nav.aap.statistikk.produksjonsstyring.api

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.produksjonsstyring.BehandlingPerAvklaringsbehov
import no.nav.aap.statistikk.produksjonsstyring.ProduksjonsstyringRepository
import java.time.LocalDate

data class BehandlingstidPerDagDTO(val dag: LocalDate, val snitt: Double)

data class BehandlingstidPerDagInput(@PathParam("typebehandling") val typeBehandling: TypeBehandling?)

data class AlderSisteDager(@PathParam("Antall dager å regne på") val antallDager: Int)

data class AntallBehandlinger(val nye: Int = 0, val avsluttede: Int = 0)

data class AntallÅpneOgGjennomsnitt(val antallÅpne: Int, val gjennomsnittsalder: Double)


enum class Tags(override val description: String) : APITag {
    Produksjonsstyring(
        "Endepunkter relatert til produksjonsstyring."
    ),
}

val modules = TagModule(listOf(Tags.Produksjonsstyring))

fun NormalOpenAPIRoute.hentBehandlingstidPerDag(
    transactionExecutor: TransactionExecutor
) {
    route("/behandlingstid/{typeBehandling}").get<BehandlingstidPerDagInput, List<BehandlingstidPerDagDTO>>(
        modules
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).hentBehandlingstidPerDag(req.typeBehandling)
        }

        respond(respons.map { BehandlingstidPerDagDTO(it.dag, it.snitt) })
    }

    route("/behandlingstid/lukkede-siste-dager/{antallDager}").get<AlderSisteDager, Double>(
        modules,
        info(description = "Henter alle behandlinger som er lukket i de siste n dager, og regner ut snittalderen på disse.")
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).alderPåFerdigeBehandlingerSisteDager(req.antallDager)
        }

        respond(respons)
    }

    route("/åpne-behandlinger").get<Unit, AntallÅpneOgGjennomsnitt>(modules) { _ ->
        val respons = transactionExecutor.withinTransaction {
            ProduksjonsstyringRepository(it).antallÅpneBehandlinger()
        }

        respond(AntallÅpneOgGjennomsnitt(respons.antallÅpne, respons.gjennomsnittsalder))
    }

    route("/behandling-per-avklaringsbehov").get<Unit, List<BehandlingPerAvklaringsbehov>>(modules) { _ ->
        val respons = transactionExecutor.withinTransaction {
            ProduksjonsstyringRepository(it).antallÅpneBehandlingerPerAvklaringsbehov()
        }

        respond(respons)
    }

    route("/behandlinger/utvikling").get<Unit, Map<LocalDate, AntallBehandlinger>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) {
        val antallBehandlinger = mutableMapOf<LocalDate, AntallBehandlinger>()
        transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            val antallNye = repo.antallNyeBehandlingerPerDag()
            val antallAvsluttede = repo.antallAvsluttedeBehandlingerPerDag()
            antallNye.forEach { antallBehandlinger[it.dag] = AntallBehandlinger(nye = it.antall) }
            antallAvsluttede.forEach {
                if (antallBehandlinger[it.dag] != null) {
                    antallBehandlinger[it.dag] =
                        antallBehandlinger[it.dag]!!.copy(avsluttede = it.antall)
                } else {
                    antallBehandlinger[it.dag] = AntallBehandlinger(avsluttede = it.antall)
                }
            }
        }
        respond(antallBehandlinger)
    }


}