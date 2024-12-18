package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører


class LagreOppgaveHendelseJobbUtfører(private val oppgaveHendelseRepository: OppgaveHendelseRepository) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<OppgaveHendelse>(input.payload())

        oppgaveHendelseRepository.lagreHendelse(hendelse)
    }

    companion object : Jobb {
        override fun beskrivelse(): String {
            return "Lagrer rene oppgavehendelser fra oppgave-appen."
        }

        override fun konstruer(connection: DBConnection): LagreOppgaveHendelseJobbUtfører {
            return LagreOppgaveHendelseJobbUtfører(OppgaveHendelseRepository(connection))
        }

        override fun navn(): String {
            return "lagreOppgaveHendelse"
        }

        override fun type(): String {
            return "lagreOppgaveHendelsejob"
        }
    }
}