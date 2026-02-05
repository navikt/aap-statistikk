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
import org.slf4j.LoggerFactory


class LagreOppgaveHendelseJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val flytJobbRepository: FlytJobbRepository
) : JobbUtfører {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<OppgaveHendelse>(input.payload())

        val forrigeHendelseVersjon = oppgaveHendelseRepository.sisteVersjonForId(hendelse.oppgaveId)
        if (forrigeHendelseVersjon != null && forrigeHendelseVersjon >= hendelse.versjon) {
            log.info("Mottok oppgave med lavere versjon enn forrige hendelse.")
            PrometheusProvider.prometheus.oppgaveHendelseMottatt(false).increment()
        } else {
            oppgaveHendelseRepository.lagreHendelse(hendelse)
            flytJobbRepository.leggTil(
                JobbInput(LagreOppgaveJobb()).medPayload(hendelse.oppgaveId.toString())
                    .forSak(hendelse.saksnummer?.let(::stringToNumber) ?: hendelse.oppgaveId)
            )
            PrometheusProvider.prometheus.oppgaveHendelseMottatt(true).increment()
        }
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