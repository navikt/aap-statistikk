package no.nav.aap.statistikk.jobber

import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository
import no.nav.aap.statistikk.jobber.appender.JobbAppender


class LagreAvsluttetHendelseDTOJobbUtfører(
    private val avsluttetBehandlingRepository: AvsluttetBehandlingRepository,
    private val jobbAppender: JobbAppender,
    val jobb: LagreAvsluttetBehandlingJobbKonstruktør
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