package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.saksstatistikk.Konstanter
import org.slf4j.LoggerFactory

class LagreStoppetHendelseJobb(
    private val jobbAppender: JobbAppender,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb
) : ProvidersJobbSpesifikasjon {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        val behandlingService = BehandlingService(repositoryProvider, gatewayProvider)

        val avsluttetBehandlingService = AvsluttetBehandlingService(
            tilkjentYtelseRepository = repositoryProvider.provide(),
            beregningsgrunnlagRepository = repositoryProvider.provide(),
            vilkårsResultatRepository = repositoryProvider.provide(),
            diagnoseRepository = repositoryProvider.provide(),
            rettighetstypeperiodeRepository = repositoryProvider.provide(),
            arbeidsopptrappingperioderRepository = repositoryProvider.provide(),
            fritaksvurderingRepository = repositoryProvider.provide(),
            behandlingService = behandlingService,
            opprettBigQueryLagringYtelseCallback = { behandlingId ->
                jobbAppender.leggTilLagreAvsluttetBehandlingTilBigQueryJobb(
                    repositoryProvider,
                    behandlingId,
                    lagreAvsluttetBehandlingTilBigQueryJobb
                )
            }
        )

        val hendelsesService = HendelsesService(
            sakService = SakService(repositoryProvider),
            personService = PersonService(repositoryProvider),
            avsluttetBehandlingService = avsluttetBehandlingService,
            behandlingService = behandlingService,
            meldekortRepository = repositoryProvider.provide<IMeldekortRepository>(),
            opprettBigQueryLagringSakStatistikkCallback = { behandlingId ->
                log.info("Legger til lagretilsaksstatistikkjobb. BehandlingId: $behandlingId")
                jobbAppender.leggTilLagreSakTilBigQueryJobb(
                    repositoryProvider,
                    behandlingId,
                    // Veldig hacky! Dette er for at jobben som kjører etter melding fra
                    // oppgave-appen skal få tid til å oppdatere enhet-tabellen før denne kjører.
                    delayInSeconds = System.getenv("HACKY_DELAY")?.toLong() ?: 0L,
                    triggerKilde = "behandling"
                )
            }
        )

        return LagreStoppetHendelseJobbUtfører(hendelsesService)
    }

    override val type = "statistikk.lagreHendelse"

    override val navn = "lagreHendelse"
    override val beskrivelse = "beskrivelse"

}