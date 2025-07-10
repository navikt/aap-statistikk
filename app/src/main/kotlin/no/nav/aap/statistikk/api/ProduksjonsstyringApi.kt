package no.nav.aap.statistikk.api

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
import org.slf4j.LoggerFactory
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
    @param:QueryParam("Hvor mange bøtter skal åpne behandlinger plasseres i?") val antallBøtter: Int?,
    @param:QueryParam("Week, month, day, etc.") val enhet: Tidsenhet = Tidsenhet.DAG,
    @param:QueryParam("Hver bøtte er enhet * bøtteStørrelse stor.") val bøtteStørrelse: Int?,
    @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
    ),
    @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<@RegularExpression(
        pattern = "[0-9]{4}[0-9]{2}?"
    ) String>? = listOf()
) {
    enum class Tidsenhet {
        DAG, UKE, MÅNED, ÅR,
    }
}

data class BehandlingerPerBehandlingstypeInputMedPeriode(
    @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    ),
    @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf(),
    @param:QueryParam("For hvilke periode som skal gjøres oppslag på") val oppslagsPeriode: OppslagsPeriode = OppslagsPeriode.IDAG
) {
    enum class OppslagsPeriode {
        IDAG,
        IGÅR,
        DENNE_UKEN,
        FORRIGE_UKE
    }
}

data class OppgaverPerBehandlingstypeInputMedPeriode(
    @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        TypeBehandling.Førstegangsbehandling
    ),
    @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf(),
    @param:QueryParam("For hvilke periode som skal gjøres oppslag på") val oppslagsPeriode: OppslagsPeriode = OppslagsPeriode.IDAG
) {
    enum class OppslagsPeriode {
        IDAG,
        IGÅR,
        DENNE_UKEN,
        FORRIGE_UKE
    }
}

private val log = LoggerFactory.getLogger("ProduksjonsstyringApi")

val modules = TagModule(listOf(Tags.Produksjonsstyring))

fun NormalOpenAPIRoute.hentBehandlingstidPerDag(
    transactionExecutor: TransactionExecutor
) {
    data class BehandlingstidPerDagInput(
        @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
        ),
        @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
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

    data class ÅpneBehandlingerPerBehandlingstypeInput(
        @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
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

    route("/åpne-behandlinger-per-behandlingstype-med-periode").get<BehandlingerPerBehandlingstypeInputMedPeriode, List<AntallÅpneOgTypeOgGjennomsnittsalder>>(
        modules,
        EndpointInfo(summary = "Antall åpne behandlinger og gjennomsnittsalder på dem per behandlingstype.")
    ) { req ->
        val respons = transactionExecutor.withinTransaction {
            val (startDato, sluttDato) = when (req.oppslagsPeriode) {
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.IDAG -> LocalDate.now() to LocalDate.now().plusDays(1)
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.IGÅR -> LocalDate.now().minusDays(1) to LocalDate.now()
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.DENNE_UKEN-> LocalDate.now().minusDays(7) to LocalDate.now()
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.FORRIGE_UKE -> LocalDate.now().minusDays(14) to LocalDate.now().minusDays(7)
                else -> LocalDate.now() to LocalDate.now().plusDays(1)
            }

            ProduksjonsstyringRepository(it).antallÅpneBehandlingerOgGjennomsnittGittPeriode(
                behandlingsTyper = req.behandlingstyper.orEmpty(),
                enheter =  req.enheter ?: listOf(),
                startDato = startDato,
                sluttDato = sluttDato
            )
        }

        respond(respons.map {
            AntallÅpneOgTypeOgGjennomsnittsalder(
                antallÅpne = it.antallÅpne, behandlingstype = it.behandlingstype,
                gjennomsnittsalder = it.gjennomsnittsalder
            )
        })
    }

    data class BehandlingerPerSteggruppeInput(
        @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf(),
        @param:QueryParam("For hvilke oppgavetyper. Tom liste betyr alle.") val oppgaveTyper: List<String>? = listOf()
    )
    route("/behandling-per-steggruppe").get<BehandlingerPerSteggruppeInput, List<BehandlingPerSteggruppe>>(
        modules
    ) { req ->
        val respons = transactionExecutor.withinTransaction { conn ->
            ProduksjonsstyringRepository(conn).antallBehandlingerPerSteggruppe(
                behandlingsTyper = req.behandlingstyper.orEmpty(),
                enheter = req.enheter.orEmpty(),
                oppgaveTyper = req.oppgaveTyper.orEmpty()
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

    route("/oppgaver-per-steggruppe-med-periode").get<OppgaverPerBehandlingstypeInputMedPeriode, OppgaverPerSteggruppe>(
        modules
    ) { req ->
        val respons = transactionExecutor.withinTransaction { connection ->
            val (startDato, sluttDato) = when (req.oppslagsPeriode) {
                OppgaverPerBehandlingstypeInputMedPeriode.OppslagsPeriode.IDAG -> LocalDate.now() to LocalDate.now().plusDays(1)
                OppgaverPerBehandlingstypeInputMedPeriode.OppslagsPeriode.IGÅR -> LocalDate.now().minusDays(1) to LocalDate.now()
                OppgaverPerBehandlingstypeInputMedPeriode.OppslagsPeriode.DENNE_UKEN-> LocalDate.now().minusDays(7) to LocalDate.now()
                OppgaverPerBehandlingstypeInputMedPeriode.OppslagsPeriode.FORRIGE_UKE -> LocalDate.now().minusDays(14) to LocalDate.now().minusDays(7)
                else -> LocalDate.now() to LocalDate.now().plusDays(1)
            }
            val repo = ProduksjonsstyringRepository(connection)
            val behandlingstyper = req.behandlingstyper.orEmpty()
            val antallNye = repo.antallÅpneOppgaver(
                behandlingsTyper = behandlingstyper,
                enheter = req.enheter.orEmpty(),
                startDato = startDato,
                sluttDato = sluttDato
            )
            val antallAvsluttede =
                repo.antallLukkedeOppgaver(
                    behandlingsTyper = behandlingstyper,
                    enheter = req.enheter.orEmpty(),
                    startDato = startDato,
                    sluttDato = sluttDato
                )
            OppgaverPerSteggruppe(antallNye, antallAvsluttede)
        }

        respond(respons)
    }

    data class BehandlingUtviklingsUtviklingInput(
        @param:QueryParam("Hvor mange dager å lage fordeling på.") val antallDager: Int = 7,
        @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
    )

    data class BehandlinEndringerPerDag(
        val dato: LocalDate, val nye: Int = 0, val avsluttede: Int = 0, val totalt: Int = 0
    )
    route("/behandlinger/utvikling").get<BehandlingUtviklingsUtviklingInput, List<BehandlinEndringerPerDag>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        log.info("Filter: behandlingstyper=${req.behandlingstyper}, enheter=${req.enheter}")
        // TODO!! for å støtte postmottak
        val antallDager = req.antallDager
        val antallBehandlinger = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)
            val behandlingstyper = req.behandlingstyper.orEmpty()
            val antallNye = repo.opprettedeBehandlingerPerDag(
                antallDager,
                behandlingstyper,
                req.enheter.orEmpty()
            )
            val antallAvsluttede =
                repo.antallAvsluttedeBehandlingerPerDag(
                    antallDager,
                    behandlingstyper,
                    req.enheter.orEmpty()
                )
            val antallÅpneBehandlinger =
                repo.antallÅpneBehandlinger(behandlingstyper, req.enheter.orEmpty())
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
        @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
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

    route("/behandlinger/på-vent-med-periode").get<BehandlingerPerBehandlingstypeInputMedPeriode, List<VenteårsakOgGjennomsnitt>>(
        TagModule(listOf(Tags.Produksjonsstyring))
    ) { req ->
        val venteårsakOgGjennomsnitt = transactionExecutor.withinTransaction { connection ->
            val repo = ProduksjonsstyringRepository(connection)

            val (startDato, sluttDato) = when (req.oppslagsPeriode) {
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.IDAG -> LocalDate.now() to LocalDate.now().plusDays(1)
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.IGÅR -> LocalDate.now().minusDays(1) to LocalDate.now()
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.DENNE_UKEN-> LocalDate.now().minusDays(7) to LocalDate.now()
                BehandlingerPerBehandlingstypeInputMedPeriode.OppslagsPeriode.FORRIGE_UKE -> LocalDate.now().minusDays(14) to LocalDate.now().minusDays(7)
                else -> LocalDate.now() to LocalDate.now().plusDays(1)
            }

            repo.venteÅrsakOgGjennomsnittGittPeriode(
                behandlingsTyper = req.behandlingstyper.orEmpty(),
                enheter = req.enheter ?: listOf(),
                startDato = startDato,
                sluttDato = sluttDato
            )
        }
        respond(venteårsakOgGjennomsnitt)
    }

    data class BehandlingAarsakAntallGjennomsnittInput(
        @param:QueryParam("For hvilke behandlingstyper. Tom liste betyr alle.") val behandlingstyper: List<TypeBehandling>? = listOf(
            TypeBehandling.Førstegangsbehandling
        ),
        @param:QueryParam("For hvilke enheter. Tom liste betyr alle.") val enheter: List<String>? = listOf()
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