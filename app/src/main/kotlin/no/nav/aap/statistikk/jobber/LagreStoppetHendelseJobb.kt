package no.nav.aap.statistikk.jobber

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.LoggingKontekst
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingService
import no.nav.aap.statistikk.avsluttetbehandling.LagreAvsluttetBehandlingTilBigQueryJobb
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.jobber.appender.MotorHendelsePublisher
import no.nav.aap.statistikk.meldekort.IMeldekortRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import org.slf4j.LoggerFactory

class LagreStoppetHendelseJobb(
    private val jobbAppender: JobbAppender,
    private val lagreAvsluttetBehandlingTilBigQueryJobb: LagreAvsluttetBehandlingTilBigQueryJobb
) : ProvidersJobbSpesifikasjon {

    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        val hendelsePublisher = MotorHendelsePublisher.medYtelseJobb(
            jobbAppender = jobbAppender,
            repositoryProvider = repositoryProvider,
            lagreAvsluttetBehandlingTilBigQueryJobb = lagreAvsluttetBehandlingTilBigQueryJobb,
        )
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
            hendelsePublisher = hendelsePublisher,
            vedtattStansOpphørRepository = repositoryProvider.provide(),
        )
        val hendelsesService = HendelsesService(
            sakService = SakService(repositoryProvider),
            personService = PersonService(repositoryProvider),
            avsluttetBehandlingService = avsluttetBehandlingService,
            behandlingService = behandlingService,
            meldekortRepository = repositoryProvider.provide<IMeldekortRepository>(),
            hendelsePublisher = hendelsePublisher,
        )
        return LagreStoppetHendelseJobbUtfører(hendelsesService)
    }

    override val type = "statistikk.lagreHendelse"

    override val navn = "lagreHendelse"
    override val beskrivelse = "beskrivelse"

}

private class LagreStoppetHendelseJobbUtfører(
    private val hendelsesService: HendelsesService
) : JobbUtfører {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val dto = DefaultJsonMapper.fromJson<StoppetBehandling>(input.payload())

        logger.info("StoppetBehandling mottatt. Behandlingsreferanse: ${dto.behandlingReferanse}.")

        LoggingKontekst(dto.behandlingReferanse).use {
            hendelsesService.prosesserNyHendelse(dto)
        }
    }
}
