package no.nav.aap.statistikk.produksjonsstyring.api

import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.hendelser.tilDomene
import no.nav.aap.statistikk.produksjonsstyring.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BehandlingstidPerDagDTO(val dag: LocalDate, val snitt: Double)

data class BehandlingstidPerDagInput(
    @PathParam(
        "typebehandling. Deprecated, vil bytte om til queryparam.", deprecated = true
    ) val typeBehandling: TypeBehandling?,
)

data class BehandlingerPåVentInput(
    @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
)


data class BehandlingerPerAvklaringsbehovInput(
    @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
)

data class BehandlingerPerSteggruppeInput(
    @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
)

data class BehandlingUtviklingsUtviklingInput(
    @QueryParam("Hvor mange dager å lage fordeling på.") val antallDager: Int = 7,
    @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
)

data class AlderSisteDager(
    @PathParam("Antall dager å regne på") val antallDager: Int? = 7,
    @QueryParam(description = "For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
)

data class ÅpneBehandlingerInput(
    @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
)

data class AntallÅpneOgGjennomsnitt(val antallÅpne: Int, val gjennomsnittsalder: Double)

data class FordelingÅpneBehandlinger(val bøtte: Int, val antall: Int) {
    companion object {
        fun fraBøtteFordeling(bøtteFordeling: BøtteFordeling): FordelingÅpneBehandlinger {
            return FordelingÅpneBehandlinger(
                bøtte = bøtteFordeling.bøtte, antall = bøtteFordeling.antall
            )
        }
    }
}

data class FordelingLukkedeBehandlinger(val bøtte: Int, val antall: Int) {
    companion object {
        fun fraBøtteFordeling(bøtteFordeling: BøtteFordeling): FordelingLukkedeBehandlinger {
            return FordelingLukkedeBehandlinger(
                bøtte = bøtteFordeling.bøtte, antall = bøtteFordeling.antall
            )
        }
    }
}

data class FordelingInput(
    @QueryParam("Hvor mange bøtter skal åpne behandlinger plasseres i?") val antallBøtter: Int?,
    @QueryParam("Week, month, day, etc.") val enhet: Tidsenhet = Tidsenhet.DAG,
    @QueryParam("Hver bøtte er enhet * bøtteStørrelse stor.") val bøtteStørrelse: Int?,
    @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    )
) {
    enum class Tidsenhet {
        DAG, UKE, MÅNED, ÅR,
    }
}

data class BehandlinEndringerPerDag(
    val dato: LocalDate, val nye: Int = 0, val avsluttede: Int = 0, val totalt: Int = 0
)

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
            ProduksjonsstyringRepository(conn).hentBehandlingstidPerDag(req.typeBehandling?.let {
                listOf(
                    it.tilDomene()
                )
            } ?: listOf())
        }

        respond(respons.map { BehandlingstidPerDagDTO(it.dag, it.snitt) })
    }

    route("/behandlingstid/lukkede-siste-dager/{antallDager}").get<AlderSisteDager, Double>(
        modules,
        info(description = "Henter alle behandlinger som er lukket i de siste n dager, og regner ut snittalderen på disse.")
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).alderPåFerdigeBehandlingerSisteDager(
                req.antallDager
                    ?: 7,
                req.behandlingstyper?.map { it.tilDomene() }
                    ?: listOf(no.nav.aap.statistikk.behandling.TypeBehandling.Førstegangsbehandling))
        }

        respond(respons)
    }

    route("/åpne-behandlinger").get<ÅpneBehandlingerInput, AntallÅpneOgGjennomsnitt>(modules) { req ->
        val respons = transactionExecutor.withinTransaction { it ->
            ProduksjonsstyringRepository(it).antallÅpneBehandlingerOgGjennomsnitt(
                (req.behandlingstyper
                    ?: listOf(TypeBehandling.Førstegangsbehandling)).map { it.tilDomene() })
        }

        respond(AntallÅpneOgGjennomsnitt(respons.antallÅpne, respons.gjennomsnittsalder))
    }

    route("/behandling-per-avklaringsbehov").get<BehandlingerPerAvklaringsbehovInput, List<BehandlingPerAvklaringsbehov>>(
        modules
    ) { req ->
        val respons = transactionExecutor.withinTransaction {
            ProduksjonsstyringRepository(it).antallÅpneBehandlingerPerAvklaringsbehov(req.behandlingstyper?.map { it.tilDomene() }
                ?: listOf())
        }

        respond(respons)
    }

    route("/behandling-per-steggruppe").get<BehandlingerPerSteggruppeInput, List<BehandlingPerSteggruppe>>(
        modules
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).antallBehandlingerPerSteggruppe(req.behandlingstyper?.map { it.tilDomene() }
                ?: listOf())
        }

        respond(respons)
    }

    route("/behandlinger/fordeling-åpne-behandlinger").get<FordelingInput, List<FordelingÅpneBehandlinger>>(
        modules, info(
            description = """
            Returnerer en liste over fordelingen på åpne behandlinger. Bøtte nr 1 teller antall
            behandlinger som er enhet * bøtteStørrelse gammel . Bøtte nr antallBøtter + 1 teller
            antall behandlinger eldre enn bøttestørrelsen.
            """.trimIndent()
        )
    ) { req ->

        respond(transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).alderÅpneBehandlinger(
                bøttestørrelse = req.bøtteStørrelse ?: 1, enhet = when (req.enhet) {
                    FordelingInput.Tidsenhet.DAG -> ChronoUnit.DAYS
                    FordelingInput.Tidsenhet.UKE -> ChronoUnit.WEEKS
                    FordelingInput.Tidsenhet.MÅNED -> ChronoUnit.MONTHS
                    FordelingInput.Tidsenhet.ÅR -> ChronoUnit.YEARS
                }, antallBøtter = req.antallBøtter ?: 30,
                behandlingsTyper = req.behandlingstyper?.map { it.tilDomene() } ?: listOf()
            ).map { FordelingÅpneBehandlinger.fraBøtteFordeling(it) }
        })
    }

    route("/behandlinger/fordeling-lukkede-behandlinger").get<FordelingInput, List<FordelingLukkedeBehandlinger>>(
        modules, info(
            description = """
            Returnerer en liste over behandlingstiden på lukkede behandlinger. Bøtte nr 1 teller antall
            behandlinger som er enhet * bøtteStørrelse gammel . Bøtte nr antallBøtter + 1 teller
            antall behandlinger eldre enn bøttestørrelsen.
            """.trimIndent()
        )
    ) { req ->

        respond(transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).alderLukkedeBehandlinger(
                bøttestørrelse = req.bøtteStørrelse ?: 1, enhet = when (req.enhet) {
                    FordelingInput.Tidsenhet.DAG -> ChronoUnit.DAYS
                    FordelingInput.Tidsenhet.UKE -> ChronoUnit.WEEKS
                    FordelingInput.Tidsenhet.MÅNED -> ChronoUnit.MONTHS
                    FordelingInput.Tidsenhet.ÅR -> ChronoUnit.YEARS
                }, antallBøtter = req.antallBøtter ?: 30,
                behandlingsTyper = req.behandlingstyper?.map { it.tilDomene() }.orEmpty()
            ).map { FordelingLukkedeBehandlinger.fraBøtteFordeling(it) }
        })
    }

    route("/behandlinger/utvikling").get<BehandlingUtviklingsUtviklingInput, List<BehandlinEndringerPerDag>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        val antallDager = req.antallDager
        val antallBehandlinger = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            val behandlingstyper = req.behandlingstyper?.map { it.tilDomene() }.orEmpty()
            val antallNye = repo.antallNyeBehandlingerPerDag(antallDager, behandlingstyper)
            val antallAvsluttede =
                repo.antallAvsluttedeBehandlingerPerDag(antallDager, behandlingstyper)
            val antallÅpneBehandlinger = repo.antallÅpneBehandlinger(behandlingstyper)
            BeregnAntallBehandlinger.antallBehandlingerPerDag(
                antallNye, antallAvsluttede, antallÅpneBehandlinger
            )
        }
        respond(antallBehandlinger.map { (k, v) ->
            BehandlinEndringerPerDag(
                dato = k, nye = v.nye, avsluttede = v.avsluttede, totalt = v.totalt
            )
        })
    }

    route("/behandlinger/på-vent").get<BehandlingerPåVentInput, List<VenteårsakOgGjennomsnitt>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        val venteårsakOgGjennomsnitt = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            repo.venteÅrsakOgGjennomsnitt(req.behandlingstyper?.map { it.tilDomene() } ?: listOf())
        }
        respond(venteårsakOgGjennomsnitt)
    }

}
