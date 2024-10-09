package no.nav.aap.statistikk.behandling

import java.util.*

interface IBehandlingRepository {
    fun opprettBehandling(behandling: Behandling): Long

    fun oppdaterBehandling(behandling: Behandling)

    fun hent(referanse: UUID): Behandling?

    fun hentEllerNull(id: Long): Behandling?

    fun hent(id: Long): Behandling

    fun tellFullførteBehandlinger(): Long
}