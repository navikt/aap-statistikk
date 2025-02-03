package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.oppgave.Enhet
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
 * @param behandlendeEnhet Hvilket NAV-kontor som eier behandlingen. Utledet som siste enhet på en oppgave tilhørende behandlingen.
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
    val behandlendeEnhet: Enhet? = null,
) {
    init {
        // Skal oppgis med sekund-presisjon
        require(
            mottattTid.truncatedTo(ChronoUnit.SECONDS).isEqual(mottattTid)
        ) { "Vil ha mottattTid på sekund-oppløsning" }
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

enum class KildeSystem {
    Behandlingsflyt, Postmottak
}

enum class TypeBehandling(val kildeSystem: KildeSystem) {
    Førstegangsbehandling(kildeSystem = KildeSystem.Behandlingsflyt),
    Revurdering(kildeSystem = KildeSystem.Behandlingsflyt),
    Tilbakekreving(kildeSystem = KildeSystem.Behandlingsflyt),
    Klage(kildeSystem = KildeSystem.Behandlingsflyt),
    Dokumenthåndtering(kildeSystem = KildeSystem.Postmottak),
    Journalføring(kildeSystem = KildeSystem.Postmottak)
}

enum class ÅrsakTilBehandling {
    SØKNAD,
    AKTIVITETSMELDING,
    MELDEKORT,
    LEGEERKLÆRING,
    AVVIST_LEGEERKLÆRING,
    DIALOGMELDING,
    G_REGULERING,
    REVURDER_MEDLEMSSKAP,
    REVURDER_YRKESSKADE,
    REVURDER_BEREGNING
}
