package no.nav.aap.statistikk.avsluttetbehandling

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.AvsluttetBehandlingDTO

interface IAvsluttetBehandlingRepository {
    fun lagre(behandling: AvsluttetBehandlingDTO): Long
    fun hent(id: Long): AvsluttetBehandlingDTO
}