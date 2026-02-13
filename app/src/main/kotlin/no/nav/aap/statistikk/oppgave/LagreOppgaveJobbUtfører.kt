package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.jobber.appender.MotorJobbAppender
import no.nav.aap.statistikk.saksstatistikk.Konstanter


class LagreOppgaveJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val oppgaveHistorikkLagrer: OppgaveHistorikkLagrer,
    private val behandlingRepository: IBehandlingRepository,
    private val repositoryProvider: RepositoryProvider,
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<Long>(input.payload())
        val hendelser = oppgaveHendelseRepository.hentHendelserForId(hendelse)

        val oppgave = hendelser.tilOppgave()

        oppgaveHistorikkLagrer.lagre(oppgave)

        if (oppgave.behandlingReferanse != null) {
            behandlingRepository.hent(oppgave.behandlingReferanse.referanse)?.let {
                if (it.typeBehandling in Konstanter.interessanteBehandlingstyper) {
                    MotorJobbAppender().leggTilLagreSakTilBigQueryJobb(repositoryProvider, it.id())
                }
            }
        }
    }
}

class LagreOppgaveJobb : ProvidersJobbSpesifikasjon {
    override fun konstruer(
        repositoryProvider: RepositoryProvider,
        gatewayProvider: GatewayProvider
    ): JobbUtfører {
        return LagreOppgaveJobbUtfører(
            repositoryProvider.provide(),
            OppgaveHistorikkLagrer.konstruer(repositoryProvider),
            repositoryProvider.provide(),
            repositoryProvider,
        )
    }

    override val type: String = "statistikk.konverterOppgavehendelserTilModell"
    override val navn: String = "Konverter oppgavehendelser til modell"
    override val beskrivelse: String =
        "Henter rene oppgavehendelser fra databasen og konverterer til modell."
}