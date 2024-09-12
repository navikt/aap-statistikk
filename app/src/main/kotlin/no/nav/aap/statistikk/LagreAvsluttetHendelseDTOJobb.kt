package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository

class LagreAvsluttetHendelseDTOJobb(private val avsluttetBehandlingRepository: AvsluttetBehandlingRepository) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        var payload = input.payload()
        var dto = DefaultJsonMapper.fromJson<AvsluttetBehandlingDTO>(payload)

        avsluttetBehandlingRepository.lagre(dto)
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return LagreAvsluttetHendelseDTOJobb(AvsluttetBehandlingRepository(connection))
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
}