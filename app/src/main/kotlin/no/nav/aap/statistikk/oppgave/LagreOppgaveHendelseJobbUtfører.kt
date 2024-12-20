package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører


class LagreOppgaveHendelseJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val flytJobbRepository: FlytJobbRepository
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<OppgaveHendelse>(input.payload())

        oppgaveHendelseRepository.lagreHendelse(hendelse)

        flytJobbRepository.leggTil(
            JobbInput(LagreOppgaveJobbUtfører).medPayload(hendelse.oppgaveId.toString())
                .forSak(hendelse.oppgaveId)
        )
    }

    companion object : Jobb {
        override fun beskrivelse(): String {
            return "Lagrer rene oppgavehendelser fra oppgave-appen."
        }

        override fun konstruer(connection: DBConnection): LagreOppgaveHendelseJobbUtfører {
            return LagreOppgaveHendelseJobbUtfører(
                OppgaveHendelseRepository(connection), FlytJobbRepository(connection)
            )
        }

        override fun navn(): String {
            return "lagreOppgaveHendelse"
        }

        override fun type(): String {
            return "statistikk.lagreOppgaveHendelseJobb"
        }
    }
}