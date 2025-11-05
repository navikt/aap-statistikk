package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.statistikk.hendelser.ResendHendelseService
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import no.nav.aap.statistikk.postgresRepositoryRegistry

class LagreAvklaringsbehovHendelseJobb(
    private val jobbAppender: JobbAppender,
) : Jobb {
    override fun konstruer(connection: DBConnection): LagreAvklaringsbehovHendelseJobbUtfører {
        return LagreAvklaringsbehovHendelseJobbUtfører(
            ResendHendelseService.konstruer(
                postgresRepositoryRegistry.provider(connection),
                jobbAppender
            )
        )
    }

    override fun type(): String {
        return "statistikk.lagreAvklaringsbehovHendelseJobb"
    }

    override fun navn(): String {
        return "LagreAvklaringsbehovHendelseJobb"
    }

    override fun beskrivelse(): String {
        return "Jobb for å regenerere behandlinghistorikk fra behandlingsflyt."
    }
}