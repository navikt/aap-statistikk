package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.oppgave.Enhet
import no.nav.aap.statistikk.oppgave.Oppgave
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
    val søknadsformat: SøknadsFormat,
    val sisteSaksbehandler: String? = null,
    val relaterteIdenter: List<String> = listOf(),
    val relatertBehandlingId: Long? = null,
    val snapShotId: Long? = null,
    val gjeldendeAvklaringsBehov: String? = null,
    val venteÅrsak: String? = null,
    val gjeldendeStegGruppe: StegGruppe? = null,
    val årsaker: List<ÅrsakTilBehandling> = listOf(),
    val oppgaver: List<Oppgave> = listOf()
) {
    init {
        // Skal oppgis med sekund-presisjon
        require(
            mottattTid.truncatedTo(ChronoUnit.SECONDS).isEqual(mottattTid)
        ) { "Vil ha mottattTid på sekund-oppløsning" }
    }

    fun enhet(): Enhet? {
        // TODO: definert som siste enhet som jobbet på oppgaven
        return oppgaver.firstOrNull()?.enhet
    }
}

enum class SøknadsFormat {
    PAPIR, DIGITAL
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

enum class ÅrsakTilBehandling {
    SØKNAD,
    AKTIVITETSMELDING,
    MELDEKORT,
    LEGEERKLÆRING,
    AVVIST_LEGEERKLÆRING,
    DIALOGMELDING,
    G_REGULERING
}
