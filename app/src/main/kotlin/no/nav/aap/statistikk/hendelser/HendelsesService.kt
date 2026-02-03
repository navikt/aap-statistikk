package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.hendelseLagret
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.saksstatistikk.Konstanter
import org.slf4j.LoggerFactory

class HendelsesService(
    private val sakService: SakService,
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val personService: PersonService,
    private val behandlingService: BehandlingService,
    private val meldekortRepository: IMeldekortRepository,
    private val opprettBigQueryLagringSakStatistikkCallback: (BehandlingId) -> Unit,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        fun konstruer(
            avsluttetBehandlingService: AvsluttetBehandlingService,
            jobbAppender: JobbAppender,
            repositoryProvider: RepositoryProvider,
            gatewayProvider: GatewayProvider
        ): HendelsesService {
            return HendelsesService(
                sakService = SakService(repositoryProvider),
                personService = PersonService(repositoryProvider),
                avsluttetBehandlingService = avsluttetBehandlingService,
                behandlingService = BehandlingService(repositoryProvider, gatewayProvider),
                meldekortRepository = repositoryProvider.provide(),
                opprettBigQueryLagringSakStatistikkCallback = {
                    LoggerFactory.getLogger(HendelsesService::class.java)
                        .info("Legger til lagretilsaksstatistikkjobb. BehandlingId: $it")
                    jobbAppender.leggTilLagreSakTilBigQueryJobb(
                        repositoryProvider,
                        it,
                        // Veldig hacky! Dette er for at jobben som kjører etter melding fra
                        // oppgave-appen skal få tid til å oppdatere enhet-tabellen før denne kjører.
                        delayInSeconds = System.getenv("HACKY_DELAY")?.toLong() ?: 0L
                    )
                }
            )
        }
    }

    fun prosesserNyHendelse(hendelse: StoppetBehandling) {
        val person = personService.hentEllerLagrePerson(hendelse.ident)
        val saksnummer = hendelse.saksnummer.let(::Saksnummer)

        val sak =
            sakService.hentEllerSettInnSak(person, saksnummer, hendelse.sakStatus.tilDomene())

        val behandlingId = behandlingService.hentEllerLagreBehandling(hendelse, sak).id()

        log.info("Mottok ${hendelse.nyeMeldekort.size} nye meldekort for behandling ${hendelse.behandlingReferanse}.")
        if (hendelse.nyeMeldekort.isNotEmpty()) {
            meldekortRepository.lagre(behandlingId, hendelse.nyeMeldekort.tilDomene())
        }

        if (hendelse.behandlingStatus == Status.AVSLUTTET) {
            val avsluttetBehandling =
                requireNotNull(hendelse.avsluttetBehandling) { "Om behandlingen er avsluttet, så må avsluttetBehandling være ikke-null." }

            // Oppfølgingsbehandling er ikke relatert til en ytelse, så dette kan ignoreres.
            if (hendelse.behandlingType.tilDomene() in listOf(
                    no.nav.aap.statistikk.behandling.TypeBehandling.Revurdering,
                    no.nav.aap.statistikk.behandling.TypeBehandling.Førstegangsbehandling
                )
            ) {
                avsluttetBehandlingService.lagre(
                    avsluttetBehandling.tilDomene(
                        saksnummer,
                        hendelse.behandlingReferanse,
                    )
                )
            }
        }

        if (hendelse.behandlingType.tilDomene() in Konstanter.interessanteBehandlingstyper) {
            opprettBigQueryLagringSakStatistikkCallback(behandlingId)
        }

        PrometheusProvider.prometheus.hendelseLagret().increment()
        log.info("Hendelse behandlet. Saksnr: ${hendelse.saksnummer}")
    }
}
