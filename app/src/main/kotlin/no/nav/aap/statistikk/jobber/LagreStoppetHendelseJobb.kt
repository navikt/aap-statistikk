package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.statistikk.hendelser.HendelsesService

class LagreStoppetHendelseJobb(
    private val hendelsesService: (DBConnection) -> HendelsesService,
) : Jobb {
    override fun konstruer(connection: DBConnection): LagreStoppetHendelseJobbUtfører {
        return LagreStoppetHendelseJobbUtfører(hendelsesService(connection))
    }

    override fun type(): String {
        return "statistikk.lagreHendelse"
    }

    override fun navn(): String {
        return "lagreHendelse"
    }

    override fun beskrivelse(): String {
        return "beskrivelse"
    }
}