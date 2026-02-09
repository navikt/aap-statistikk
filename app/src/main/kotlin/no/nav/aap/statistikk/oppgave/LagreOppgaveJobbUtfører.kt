package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.gateway.GatewayProvider
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProvidersJobbSpesifikasjon
import no.nav.aap.statistikk.behandling.IBehandlingRepository
import no.nav.aap.statistikk.saksstatistikk.Konstanter
import no.nav.aap.statistikk.saksstatistikk.SaksStatistikkService


class LagreOppgaveJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val oppgaveHistorikkLagrer: OppgaveHistorikkLagrer,
    private val sakstatistikkService: SaksStatistikkService,
    private val behandlingRepository: IBehandlingRepository,
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<Long>(input.payload())
        val hendelser = oppgaveHendelseRepository.hentHendelserForId(hendelse)

        val oppgave = hendelser.tilOppgave()

        oppgaveHistorikkLagrer.lagre(oppgave)

        if (oppgave.behandlingReferanse != null) {
            behandlingRepository.hent(oppgave.behandlingReferanse.referanse)?.let {
                if (it.typeBehandling in Konstanter.interessanteBehandlingstyper) {
                    sakstatistikkService.lagreSakInfoTilBigqueryFraOppgave(it.id())
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
            SaksStatistikkService.konstruer(gatewayProvider, repositoryProvider),
            repositoryProvider.provide()
        )
    }

    override val type: String = "statistikk.konverterOppgavehendelserTilModell"
    override val navn: String = "Konverter oppgavehendelser til modell"
    override val beskrivelse: String =
        "Henter rene oppgavehendelser fra databasen og konverterer til modell."
}