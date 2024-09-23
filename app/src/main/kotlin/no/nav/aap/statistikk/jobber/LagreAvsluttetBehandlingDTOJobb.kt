package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository
import no.nav.aap.statistikk.jobber.appender.InniJobbJobbAppender

class LagreAvsluttetBehandlingDTOJobb(
    val jobb: LagreAvsluttetBehandlingJobbKonstruktør
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return LagreAvsluttetHendelseDTOJobbUtfører(
            AvsluttetBehandlingRepository(connection),
            InniJobbJobbAppender(connection),
            jobb,
        )
    }

    override fun type(): String {
        return "lagreAvsluttetBehandlingDTO"
    }

    override fun navn(): String {
        return "Lagre avsluttet behandling"
    }

    override fun beskrivelse(): String {
        return "lagre avsluttet behandling"
    }
}