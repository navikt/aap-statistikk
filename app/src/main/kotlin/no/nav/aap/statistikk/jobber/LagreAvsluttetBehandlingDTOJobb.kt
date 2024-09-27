package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.Counter
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository
import no.nav.aap.statistikk.jobber.appender.InniJobbJobbAppender

class LagreAvsluttetBehandlingDTOJobb(
    private val jobb: LagreAvsluttetBehandlingJobbKonstruktør,
    private val avsluttetBehandlingDtoLagretCounter: Counter
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return LagreAvsluttetHendelseDTOJobbUtfører(
            AvsluttetBehandlingRepository(connection),
            InniJobbJobbAppender(connection),
            jobb,
            avsluttetBehandlingDtoLagretCounter,
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