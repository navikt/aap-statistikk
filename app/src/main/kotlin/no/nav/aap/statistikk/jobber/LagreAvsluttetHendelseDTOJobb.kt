package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository


class LagreAvsluttetBehandlingDTOJobb(
    val jobb: LagreAvsluttetBehandlingJobb
) : Jobb {
    override fun konstruer(connection: DBConnection): JobbUtfører {
        return LagreAvsluttetHendelseDTOJobb(
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

class LagreAvsluttetHendelseDTOJobb(
    private val avsluttetBehandlingRepository: AvsluttetBehandlingRepository,
    private val jobbAppender: JobbAppender,
    val jobb: LagreAvsluttetBehandlingJobb
) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        var payload = input.payload()
        var dto = DefaultJsonMapper.fromJson<AvsluttetBehandlingDTO>(payload)

        val id = avsluttetBehandlingRepository.lagre(dto)

        jobbAppender.leggTil(
            JobbInput(jobb).medParameter("id", id.toString())
        )
    }
}