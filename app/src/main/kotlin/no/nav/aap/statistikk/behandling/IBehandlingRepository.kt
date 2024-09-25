package no.nav.aap.statistikk.behandling

import java.util.UUID

interface IBehandlingRepository {
    fun lagre(behandling: Behandling): Long

    fun hent(referanse: UUID): Behandling?
}