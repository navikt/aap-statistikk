package no.nav.aap.statistikk.oversikt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.sak.SakRepositoryImpl

internal fun Routing.oversiktRoute(transactionExecutor: TransactionExecutor) {
    get("/") {
        val name = "Statistikkoversikt"

        val antallSaker = transactionExecutor.withinTransaction {
            SakRepositoryImpl(it).tellSaker()
        }
        val antallFullførteBehandlinger = transactionExecutor.withinTransaction {
            BehandlingRepository(it).tellFullførteBehandlinger()
        }
        call.respondHtml(HttpStatusCode.Companion.OK) {
            head {
                title {
                    +name
                }
            }
            body {
                h1 {
                    +"Statistikkoversikt"
                }
                p {
                    +"Antall saker: $antallSaker"
                }
                p {
                    +"Antall fullførte behandlinger: $antallFullførteBehandlinger"
                }
            }
        }
    }
}