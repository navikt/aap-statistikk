package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.komponenter.repository.RepositoryRegistry
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.jobber.appender.JobbAppender


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

class LagreOppgaveJobb(
    private val jobbAppender: JobbAppender,
    private val repositoryRegistry: RepositoryRegistry
) : Jobb {
    override fun beskrivelse(): String {
        return "Henter rene oppgavehendelser fra databasen og konverterer til modell."
    }

    override fun konstruer(connection: DBConnection): LagreOppgaveJobbUtfører {
        return LagreOppgaveJobbUtfører(
            OppgaveHendelseRepository(connection),
            OppgaveHistorikkLagrer.konstruer(connection, repositoryRegistry.provider(connection), {
                jobbAppender.leggTilLagreSakTilBigQueryJobb(
                    connection,
                    it
                )
            })
        )
    }

    override fun navn(): String {
        return "Konverter oppgavehendelser til modell"
    }

    override fun type(): String {
        return "statistikk.konverterOppgavehendelserTilModell"
    }
}