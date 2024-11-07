package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.sak.Sak
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

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
    val sisteSaksbehandler: String? = null,
    val relaterteIdenter: List<String> = listOf(),
    val relatertBehandlingId: Long? = null,
    val snapShotId: Long? = null,
    val gjeldendeAvklaringsBehov: String? = null,
) {
    init {
        // Skal oppgis med sekund-presisjon
        require(
            mottattTid.truncatedTo(ChronoUnit.SECONDS).isEqual(mottattTid)
        ) { "Vil ha mottattTid på sekund-oppløsning" }
    }
}


enum class BehandlingStatus {
    OPPRETTET,
    UTREDES,
    IVERKSETTES,
    AVSLUTTET;
}


enum class TypeBehandling {
    Førstegangsbehandling,
    Revurdering,
    Tilbakekreving,
    Klage;
}

fun no.nav.aap.behandlingsflyt.kontrakt.statistikk.TypeBehandling.tilDomene(): TypeBehandling =
    when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.TypeBehandling.Førstegangsbehandling -> TypeBehandling.Førstegangsbehandling
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.TypeBehandling.Revurdering -> TypeBehandling.Revurdering
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.TypeBehandling.Tilbakekreving -> TypeBehandling.Tilbakekreving
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.TypeBehandling.Klage -> TypeBehandling.Klage
    }

fun no.nav.aap.behandlingsflyt.kontrakt.statistikk.BehandlingStatus.tilDomene(): BehandlingStatus =
    when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.BehandlingStatus.OPPRETTET -> BehandlingStatus.OPPRETTET
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.BehandlingStatus.UTREDES -> BehandlingStatus.UTREDES
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.BehandlingStatus.IVERKSETTES -> BehandlingStatus.IVERKSETTES
        no.nav.aap.behandlingsflyt.kontrakt.statistikk.BehandlingStatus.AVSLUTTET -> BehandlingStatus.AVSLUTTET
    }
