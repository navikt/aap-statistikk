package no.nav.aap.statistikk.behandling

import java.util.*

interface IBehandlingRepository {
    fun lagre(behandling: Behandling): Long

    fun hent(referanse: UUID): Behandling?

    fun hentEllerNull(id: Long): Behandling?

    fun hent(id: Long): Behandling
}