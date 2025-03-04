package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.annotations.type.string.pattern.RegularExpression
import com.papsign.ktor.openapigen.route.EndpointInfo
import com.papsign.ktor.openapigen.route.TagModule
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.produksjonsstyring.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit


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
    ),
    @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<@RegularExpression(
        pattern = "[0-9]{4}[0-9]{2}?"
    ) String>? = listOf()
) {
    enum class Tidsenhet {
        DAG, UKE, MÅNED, ÅR,
    }
}

val modules = TagModule(listOf(Tags.Produksjonsstyring))

fun NormalOpenAPIRoute.hentBehandlingstidPerDag(
    transactionExecutor: TransactionExecutor
) {
    data class BehandlingstidPerDagInput(
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )

    data class BehandlingstidPerDagDTO(val dag: LocalDate, val snitt: Double)
    route("/behandlingstid").get<BehandlingstidPerDagInput, List<BehandlingstidPerDagDTO>>(
        modules,
        EndpointInfo(summary = "For en gitt dag, hva er gjennomsnittlig alder på alle behandlinger som ble avsluttet på denne dagen.")
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).hentBehandlingstidPerDag(
                req.behandlingstyper ?: listOf(), req.enheter.orEmpty()
            )
        }

        respond(respons.map { BehandlingstidPerDagDTO(it.dag, it.snitt) })
    }

    data class AlderSisteDager(
        @PathParam("Antall dager å regne på") val antallDager: Int? = 7,
        @QueryParam(description = "For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )
    route("/behandlingstid/lukkede-siste-dager/{antallDager}").get<AlderSisteDager, Double>(
        modules,
        info(description = "Henter alle behandlinger som er lukket i de siste n dager, og regner ut snittalderen på disse.")
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).alderPåFerdigeBehandlingerSisteDager(
                req.antallDager
                    ?: 7,
                req.behandlingstyper.orEmpty(),
                req.enheter.orEmpty()
            )
        }

        respond(respons)
    }

    data class ÅpneBehandlingerPerBehandlingstypeInput(
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )

    data class AntallÅpneOgTypeOgGjennomsnittsalder(
        val antallÅpne: Int,
        val behandlingstype: TypeBehandling,
        val gjennomsnittsalder: Double
    )
    route("/åpne-behandlinger-per-behandlingstype").get<ÅpneBehandlingerPerBehandlingstypeInput, List<AntallÅpneOgTypeOgGjennomsnittsalder>>(
        modules,
        EndpointInfo(summary = "Antall åpne behandlinger og gjennomsnittsalder på dem per behandlingstype.")
    ) { req ->
        val respons = transactionExecutor.withinTransaction {
            ProduksjonsstyringRepository(it).antallÅpneBehandlingerOgGjennomsnitt(
                req.behandlingstyper.orEmpty(), req.enheter ?: listOf()
            )
        }

        respond(respons.map {
            AntallÅpneOgTypeOgGjennomsnittsalder(
                antallÅpne = it.antallÅpne, behandlingstype = it.behandlingstype,
                gjennomsnittsalder = it.gjennomsnittsalder
            )
        })
    }

    data class BehandlingerPerAvklaringsbehovInput(
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )
    route("/behandling-per-avklaringsbehov").get<BehandlingerPerAvklaringsbehovInput, List<BehandlingPerAvklaringsbehov>>(
        modules, EndpointInfo(
            summary = "Antall åpne behandlinger per avklaringsbehov.",
        )
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).antallÅpneBehandlingerPerAvklaringsbehov(
                req.behandlingstyper.orEmpty(),
                req.enheter ?: listOf()
            )
        }

        respond(respons)
    }

    data class BehandlingerPerSteggruppeInput(
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )
    route("/behandling-per-steggruppe").get<BehandlingerPerSteggruppeInput, List<BehandlingPerSteggruppe>>(
        modules
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).antallBehandlingerPerSteggruppe(
                req.behandlingstyper.orEmpty(),
                req.enheter.orEmpty()
            )
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
                bøttestørrelse = req.bøtteStørrelse
                    ?: 1,
                enhet = when (req.enhet) {
                    FordelingInput.Tidsenhet.DAG -> ChronoUnit.DAYS
                    FordelingInput.Tidsenhet.UKE -> ChronoUnit.WEEKS
                    FordelingInput.Tidsenhet.MÅNED -> ChronoUnit.MONTHS
                    FordelingInput.Tidsenhet.ÅR -> ChronoUnit.YEARS
                },
                antallBøtter = req.antallBøtter ?: 30,
                behandlingsTyper = req.behandlingstyper.orEmpty(),
                enheter = req.enheter.orEmpty()
            )
                .map { FordelingÅpneBehandlinger.fraBøtteFordeling(it) }
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
                bøttestørrelse = req.bøtteStørrelse ?: 1,
                enhet = when (req.enhet) {
                    FordelingInput.Tidsenhet.DAG -> ChronoUnit.DAYS
                    FordelingInput.Tidsenhet.UKE -> ChronoUnit.WEEKS
                    FordelingInput.Tidsenhet.MÅNED -> ChronoUnit.MONTHS
                    FordelingInput.Tidsenhet.ÅR -> ChronoUnit.YEARS
                },
                antallBøtter = req.antallBøtter ?: 30,
                behandlingsTyper = req.behandlingstyper.orEmpty(),
                enheter = req.enheter.orEmpty()
            ).map { FordelingLukkedeBehandlinger.fraBøtteFordeling(it) }
        })
    }

    data class BehandlingUtviklingsUtviklingInput(
        @QueryParam("Hvor mange dager å lage fordeling på.") val antallDager: Int = 7,
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )

    data class BehandlinEndringerPerDag(
        val dato: LocalDate, val nye: Int = 0, val avsluttede: Int = 0, val totalt: Int = 0
    )
    route("/behandlinger/utvikling").get<BehandlingUtviklingsUtviklingInput, List<BehandlinEndringerPerDag>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        // TODO!! for å støtte postmottak
        val antallDager = req.antallDager
        val antallBehandlinger = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            val behandlingstyper = req.behandlingstyper.orEmpty()
            val antallNye = repo.opprettedeBehandlingerPerDag(antallDager, behandlingstyper)
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

    data class BehandlingerPåVentInput(
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )
    route("/behandlinger/på-vent").get<BehandlingerPåVentInput, List<VenteårsakOgGjennomsnitt>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        val venteårsakOgGjennomsnitt = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            repo.venteÅrsakOgGjennomsnitt(
                req.behandlingstyper.orEmpty(), req.enheter ?: listOf()
            )
        }
        respond(venteårsakOgGjennomsnitt)
    }

    data class BehandlingAarsakAntallGjennomsnittInput(
        @QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )
    route("/behandlinger/årsak-til-behandling").get<BehandlingAarsakAntallGjennomsnittInput, List<BehandlingAarsakAntallGjennomsnitt>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        val årsakOgGjennomsnitt = transactionExecutor.withinTransaction {
            ProduksjonsstyringRepository(it).antallBehandlingerPerÅrsak(
                req.behandlingstyper.orEmpty(), req.enheter ?: listOf()
            )
        }

        respond(årsakOgGjennomsnitt.map {
            BehandlingAarsakAntallGjennomsnitt(
                årsak = it.årsak, antall = it.antall, gjennomsnittligAlder = it.gjennomsnittligAlder
            )
        })
    }

}