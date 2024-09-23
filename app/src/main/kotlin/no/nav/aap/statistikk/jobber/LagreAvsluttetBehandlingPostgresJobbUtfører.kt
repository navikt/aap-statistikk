package no.nav.aap.statistikk.jobber

import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.statistikk.avsluttetbehandling.IAvsluttetBehandlingRepository
import no.nav.aap.statistikk.avsluttetbehandling.api.tilDomene
import no.nav.aap.statistikk.avsluttetbehandling.service.AvsluttetBehandlingService

class LagreAvsluttetBehandlingPostgresJobbUtfører(
    private val avsluttetBehandlingService: AvsluttetBehandlingService,
    private val avsluttetBehandlingRepository: IAvsluttetBehandlingRepository
) :
    JobbUtfører {
    override fun utfør(input: JobbInput) {
        val id = input.parameter("id").toLong()

        val dto = avsluttetBehandlingRepository.hent(id)

        avsluttetBehandlingService.lagre(dto.tilDomene())
    }
}