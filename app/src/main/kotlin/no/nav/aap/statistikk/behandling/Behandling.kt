package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.oppgave.Enhet
import no.nav.aap.statistikk.oppgave.Saksbehandler
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
    val vedtakstidspunkt: LocalDateTime? = null,
    val ansvarligBeslutter: String? = null,
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
    val hendelser: List<BehandlingHendelse> = listOf(),
) {
    init {
        // Skal oppgis med sekund-presisjon
        require(
            mottattTid.truncatedTo(ChronoUnit.SECONDS).isEqual(mottattTid)
        ) { "Vil ha mottattTid på sekund-oppløsning" }

        require((ansvarligBeslutter != null && vedtakstidspunkt != null) || (ansvarligBeslutter == null && vedtakstidspunkt == null)) {
            "Om saken er besluttet, så må både vedtakstidspunkt og ansvarlig beslutter være ikke-null. Har ansvarlig beslutter: $ansvarligBeslutter, har vedtakstidspunkt: $vedtakstidspunkt"
        }
    }

    fun avsluttetTid(): LocalDateTime {
        return hendelser
            .filter { it.status == BehandlingStatus.AVSLUTTET }
            .maxOf { it.tidspunkt }
    }
}

data class BehandlingHendelse(
    val tidspunkt: LocalDateTime,
    val avklaringsBehov: String? = null,
    val saksbehandler: Saksbehandler? = null,
    val status: BehandlingStatus,
)

enum class SøknadsFormat {
    PAPIR, DIGITAL
}


enum class BehandlingStatus {
    OPPRETTET, UTREDES, IVERKSETTES, AVSLUTTET;
}

enum class KildeSystem {
    Behandlingsflyt, Postmottak
}

enum class TypeBehandling(val kildeSystem: KildeSystem) {
    Førstegangsbehandling(kildeSystem = KildeSystem.Behandlingsflyt), Revurdering(kildeSystem = KildeSystem.Behandlingsflyt), Tilbakekreving(
        kildeSystem = KildeSystem.Behandlingsflyt
    ),
    Klage(kildeSystem = KildeSystem.Behandlingsflyt), Dokumenthåndtering(kildeSystem = KildeSystem.Postmottak), Journalføring(
        kildeSystem = KildeSystem.Postmottak
    )
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
    REVURDER_BEREGNING,
    REVURDER_LOVVALG,
    KLAGE,
    REVURDER_SAMORDNING,
    LOVVALG_OG_MEDLEMSKAP,
    FORUTGAENDE_MEDLEMSKAP,
    SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND,
    BARNETILLEGG,
    INSTITUSJONSOPPHOLD,
    SAMORDNING_OG_AVREGNING,
    REFUSJONSKRAV,
    UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT,
}
