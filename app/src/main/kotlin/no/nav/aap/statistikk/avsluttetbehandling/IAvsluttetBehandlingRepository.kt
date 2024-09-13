package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.statistikk.api_kontrakt.AvsluttetBehandlingDTO

interface IAvsluttetBehandlingRepository {
    fun lagre(behandling: AvsluttetBehandlingDTO): Long
    fun hent(id: Long): AvsluttetBehandlingDTO
}