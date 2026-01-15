package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.ProviderJobbSpesifikasjon
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.oppgaveHendelseMottatt


class LagreOppgaveHendelseJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val flytJobbRepository: FlytJobbRepository
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<OppgaveHendelse>(input.payload())

        oppgaveHendelseRepository.lagreHendelse(hendelse)

        flytJobbRepository.leggTil(
            JobbInput(LagreOppgaveJobb()).medPayload(hendelse.oppgaveId.toString())
                .forSak(hendelse.saksnummer?.let(::stringToNumber) ?: hendelse.oppgaveId)
        )
        PrometheusProvider.prometheus.oppgaveHendelseMottatt().increment()
    }
}

class LagreOppgaveHendelseJobb : ProviderJobbSpesifikasjon {
    override fun konstruer(repositoryProvider: RepositoryProvider): JobbUtfører {
        return LagreOppgaveHendelseJobbUtfører(
            repositoryProvider.provide(),
            repositoryProvider.provide()
        )
    }

    override val type: String = "statistikk.lagreOppgaveHendelseJobb"
    override val navn: String = "lagreOppgaveHendelse"

    override val beskrivelse: String = "Lagrer rene oppgavehendelser fra oppgave-appen."
}