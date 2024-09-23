package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.sak.Sak
import java.time.LocalDateTime
import java.util.UUID

data class Behandling(
    val id: Long? = null,
    val referanse: UUID,
    val sak: Sak,
    val typeBehandling: TypeBehandling,
    val opprettetTid: LocalDateTime
)