package no.nav.aap.statistikk.oversikt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.title
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.hendelser.repository.HendelsesRepository

internal fun Routing.oversiktRoute(transactionExecutor: TransactionExecutor) {
    get("/") {
        val name = "Ktor"
        val antallHendelser =
            transactionExecutor.withinTransaction { HendelsesRepository(it).tellHendelser() }
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
                    +"Antall hendelser: $antallHendelser"
                }
            }
        }
    }
}