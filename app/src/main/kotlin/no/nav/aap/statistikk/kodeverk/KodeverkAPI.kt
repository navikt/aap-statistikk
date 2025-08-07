package no.nav.aap.statistikk.kodeverk

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.body
import kotlinx.html.*
import kotlinx.html.head
import kotlinx.html.title
import no.nav.aap.statistikk.db.TransactionExecutor

fun Application.kodeverk(
    transactionExecutor: TransactionExecutor,
) {
    routing {
        get("/kodeverk") {
            val name = "Ktor"
            val behandlingstyper = transactionExecutor.withinTransaction {
                KodeverkRepository(it).hentBehandlingType()
            }
            val vilkår = transactionExecutor.withinTransaction {
                KodeverkRepository(it).hentVilkår()
            }
            val resultat = transactionExecutor.withinTransaction {
                KodeverkRepository(it).hentResultat()
            }
            val rettighetsType = transactionExecutor.withinTransaction {
                KodeverkRepository(it).hentRettighetstype()
            }

            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title {
                        +name
                    }

                    unsafe {
                        raw(
                            """
                                <style>
                                th {
                                border-bottom: 1px solid black;
                                }
                               </style>
                            """.trimIndent()
                        )
                    }
                }
                body {
                    h1 {
                        +"Kodeverk AAP-statistikk"
                    }
                    kodeverkTabell("Behandlingstyper", behandlingstyper)
                    kodeverkTabell("Vilkår", vilkår)
                    kodeverkTabell("Resultat", resultat)
                    kodeverkTabell("Rettighetstype", rettighetsType)
                }
            }

        }
    }
}

private fun BODY.kodeverkTabell(title: String, kodeverk: List<Kodeverk>) {
    p {
        h2 {
            +title
        }
        table {
            thead {
                tr {
                    th { +"Kode" }
                    th { +"Beskrivelse" }
                    th { +"Gyldig fra" }
                    th { +"Gyldig til" }
                }
            }
            tbody {
                kodeverk.forEach {
                    tr {
                        td { +it.kode }
                        td { +it.beskrivelse }
                        td { +it.gyldigFra.toString() }
                        td { +it.gyldigTil.toString() }
                    }
                }
            }
        }
    }
}