package no.nav.aap.statistikk.produksjonsstyring.api

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.produksjonsstyring.ProduksjonsstyringRepository
import java.time.LocalDate

data class BehandlingstidPerDagDTO(val dag: LocalDate, val snitt: Double)

data class BehandlingstidPerDagInput(@PathParam("typebehandling") val typeBehandling: TypeBehandling?)

fun NormalOpenAPIRoute.hentBehandlingstidPerDag(
    transactionExecutor: TransactionExecutor
) {

    route("/behandlingstid/{typeBehandling}").get<BehandlingstidPerDagInput, List<BehandlingstidPerDagDTO>> { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).hentBehandlingstidPerDag(req.typeBehandling)
        }

        respond(respons.map { BehandlingstidPerDagDTO(it.dag, it.snitt) })
    }
}