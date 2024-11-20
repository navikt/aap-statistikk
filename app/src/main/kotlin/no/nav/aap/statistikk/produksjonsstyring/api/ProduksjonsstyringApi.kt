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
import no.nav.aap.statistikk.produksjonsstyring.BehandlingPerAvklaringsbehov
import no.nav.aap.statistikk.produksjonsstyring.BeregnAntallBehandlinger
import no.nav.aap.statistikk.produksjonsstyring.FordelingÅpneBehandlinger
import no.nav.aap.statistikk.produksjonsstyring.ProduksjonsstyringRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BehandlingstidPerDagDTO(val dag: LocalDate, val snitt: Double)

data class BehandlingstidPerDagInput(@PathParam("typebehandling") val typeBehandling: TypeBehandling?)

data class BehandlingUtviklingsUtviklingInput(@QueryParam("Hvor mange dager å lage fordelingpå.") val antallDager: Int = 7)

data class AlderSisteDager(@PathParam("Antall dager å regne på") val antallDager: Int)

data class AntallÅpneOgGjennomsnitt(val antallÅpne: Int, val gjennomsnittsalder: Double)

data class FordelingÅpneBehandlingerInput(
    @QueryParam("Hvor mange bøtter skal åpne behandlinger plasseres i?") val antallBøtter: Int?,
    @QueryParam("Week, month, day, etc.") val enhet: ChronoUnit = ChronoUnit.DAYS,
    @QueryParam("Hver bøtte er enhet * bøtteStørrelse stor.") val bøtteStørrelse: Int?
)

data class BehandlinEndringerPerDag(
    val dato: LocalDate,
    val nye: Int = 0,
    val avsluttede: Int = 0,
    val totalt: Int = 0
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
            ProduksjonsstyringRepository(conn).hentBehandlingstidPerDag(req.typeBehandling?.tilDomene())
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
            ProduksjonsstyringRepository(it).antallÅpneBehandlingerOgGjennomsnitt()
        }

        respond(AntallÅpneOgGjennomsnitt(respons.antallÅpne, respons.gjennomsnittsalder))
    }

    route("/behandling-per-avklaringsbehov").get<Unit, List<BehandlingPerAvklaringsbehov>>(modules) { _ ->
        val respons = transactionExecutor.withinTransaction {
            ProduksjonsstyringRepository(it).antallÅpneBehandlingerPerAvklaringsbehov()
        }

        respond(respons)
    }

    route("/behandlinger/fordeling-åpne-behandlinger").get<FordelingÅpneBehandlingerInput, List<FordelingÅpneBehandlinger>>(
        modules,
        info(
            description = """
            Returnerer en liste over fordelingen på åpne behandlinger. Bøtte nr 1 teller antall behandlinger som er én dag. Bøtte nr 31 teller antall behandlinger eldre enn 30 dager.
            """.trimIndent()
        )
    ) { req ->

        respond(transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).alderÅpneBehandlinger(
                bøttestørrelse = req.bøtteStørrelse ?: 1,
                enhet = req.enhet,
                antallBøtter = req.antallBøtter ?: 30
            )
        })
    }

    route("/behandlinger/utvikling").get<BehandlingUtviklingsUtviklingInput, List<BehandlinEndringerPerDag>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        val antallDager = req.antallDager
        val antallBehandlinger = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            val antallNye = repo.antallNyeBehandlingerPerDag(antallDager)
            val antallAvsluttede = repo.antallAvsluttedeBehandlingerPerDag(antallDager)
            val antallÅpneBehandlinger = repo.antallÅpneBehandlinger()
            BeregnAntallBehandlinger.antallBehandlingerPerDag(
                antallNye,
                antallAvsluttede,
                antallÅpneBehandlinger
            )
        }
        respond(antallBehandlinger.map { (k, v) ->
            BehandlinEndringerPerDag(
                dato = k,
                nye = v.nye,
                avsluttede = v.avsluttede,
                totalt = v.totalt
            )
        })
    }

}
