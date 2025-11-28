package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProviderJobbSpesifikasjon


class LagreOppgaveJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val oppgaveHistorikkLagrer: OppgaveHistorikkLagrer
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<Long>(input.payload())
        val hendelser = oppgaveHendelseRepository.hentHendelserForId(hendelse)

        val oppgave = hendelser.tilOppgave()

        oppgaveHistorikkLagrer.lagre(oppgave)
    }
}

class LagreOppgaveJobb : ProviderJobbSpesifikasjon {
    override fun konstruer(repositoryProvider: RepositoryProvider): JobbUtfører {
        val repositoryProvider = repositoryProvider
        return LagreOppgaveJobbUtfører(
            repositoryProvider.provide(),
            OppgaveHistorikkLagrer.konstruer(repositoryProvider)
        )
    }

    override val type: String = "statistikk.konverterOppgavehendelserTilModell"
    override val navn: String = "Konverter oppgavehendelser til modell"
    override val beskrivelse: String =
        "Henter rene oppgavehendelser fra databasen og konverterer til modell."
}