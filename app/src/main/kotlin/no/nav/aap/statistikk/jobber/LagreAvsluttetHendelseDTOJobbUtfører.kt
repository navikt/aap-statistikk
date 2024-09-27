package no.nav.aap.statistikk.jobber

import io.micrometer.core.instrument.Counter
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO
import no.nav.aap.statistikk.avsluttetbehandling.AvsluttetBehandlingRepository
import no.nav.aap.statistikk.jobber.appender.JobbAppender


class LagreAvsluttetHendelseDTOJobbUtfører(
    private val avsluttetBehandlingRepository: AvsluttetBehandlingRepository,
    private val jobbAppender: JobbAppender,
    private val jobb: LagreAvsluttetBehandlingJobbKonstruktør,
    private val avsluttetBehandlingDtoLagretCounter: Counter
) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val payload = input.payload()
        val dto = DefaultJsonMapper.fromJson<AvsluttetBehandlingDTO>(payload)

        val id = avsluttetBehandlingRepository.lagre(dto)

        jobbAppender.leggTil(
            JobbInput(jobb).medParameter("id", id.toString())
        )

        avsluttetBehandlingDtoLagretCounter.increment()
    }
}