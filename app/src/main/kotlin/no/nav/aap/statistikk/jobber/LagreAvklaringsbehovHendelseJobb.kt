package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.statistikk.hendelser.HendelsesService

class LagreAvklaringsbehovHendelseJobb(
    private val hendelsesService: (DBConnection) -> HendelsesService,
) : Jobb {
    override fun konstruer(connection: DBConnection): LagreAvklaringsbehovHendelseJobbUtfører {
        return LagreAvklaringsbehovHendelseJobbUtfører(hendelsesService(connection))
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