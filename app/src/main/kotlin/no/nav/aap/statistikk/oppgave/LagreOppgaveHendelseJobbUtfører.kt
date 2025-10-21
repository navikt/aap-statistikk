package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.PrometheusProvider
import no.nav.aap.statistikk.api.stringToNumber
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.oppgaveHendelseMottatt


class LagreOppgaveHendelseJobbUtfører(
    private val oppgaveHendelseRepository: OppgaveHendelseRepository,
    private val flytJobbRepository: FlytJobbRepository,
    private val jobbAppender: JobbAppender
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelse = DefaultJsonMapper.fromJson<OppgaveHendelse>(input.payload())

        oppgaveHendelseRepository.lagreHendelse(hendelse)

        flytJobbRepository.leggTil(
            JobbInput(LagreOppgaveJobb(jobbAppender)).medPayload(hendelse.oppgaveId.toString())
                .forSak(hendelse.saksnummer?.let(::stringToNumber) ?: hendelse.oppgaveId)
        )
        PrometheusProvider.prometheus.oppgaveHendelseMottatt().increment()
    }
}

class LagreOppgaveHendelseJobb(
    private val jobbAppender: JobbAppender
) : Jobb {
    override fun beskrivelse(): String {
        return "Lagrer rene oppgavehendelser fra oppgave-appen."
    }

    override fun konstruer(connection: DBConnection): LagreOppgaveHendelseJobbUtfører {
        return LagreOppgaveHendelseJobbUtfører(
            OppgaveHendelseRepository(connection),
            FlytJobbRepository(connection),
            jobbAppender,
        )
    }

    override fun navn(): String {
        return "lagreOppgaveHendelse"
    }

    override fun type(): String {
        return "statistikk.lagreOppgaveHendelseJobb"
    }
}