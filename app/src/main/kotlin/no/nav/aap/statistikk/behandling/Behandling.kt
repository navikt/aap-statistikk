package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.api_kontrakt.BehandlingStatus
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.sak.Sak
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Versjon(
    val verdi: String,
    val id: Long? = null,
)

/**
 * @param versjon Applikasjonsversjon fra behandlingsflyt på denne behandlingen.
 */
data class Behandling(
    val id: Long? = null,
    val referanse: UUID,
    val sak: Sak,
    val typeBehandling: TypeBehandling,
    val status: BehandlingStatus,
    val opprettetTid: LocalDateTime,
    val mottattTid: LocalDateTime,
    val versjon: Versjon,
    val relatertBehandlingId: Long? = null,
    val snapShotId: Long? = null,
) {
    init {
        // Skal oppgis med sekund-presisjon
        require(mottattTid.truncatedTo(ChronoUnit.SECONDS).isEqual(mottattTid))
    }
}