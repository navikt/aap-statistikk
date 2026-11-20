package no.nav.aap.statistikk.behandling

import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.utbetaling.helved.toBase64
import java.time.LocalDateTime
import java.util.UUID

@JvmInline
value class Versjon(val verdi: String)

/**
 * @param relatertBehandlingId Referanse til en relatert Kelvin-behandling.
 */
data class Behandling(
    val id: BehandlingId? = null,
    val referanse: UUID,
    val sak: Sak,
    val typeBehandling: TypeBehandling,
    private val tilstand: BehandlingTilstand,
    val opprettetTid: LocalDateTime,
    val relaterteIdenter: List<Ident> = listOf(),
    val relatertBehandlingId: BehandlingId? = null,
    val snapShotId: Long? = null,
    val årsaker: List<Vurderingsbehov> = listOf(),
    val årsakTilOpprettelse: String? = null,
    private val oppdatertTidspunkt: LocalDateTime? = null,
    val hendelser: List<BehandlingHendelse> = listOf(),
    val opprettetAv: String? = null,
) {
    init {
        val sisteLøsteAvklaringsbehov = tilstand.sisteLøsteAvklaringsbehov
        val sisteSaksbehandlerSomLøstebehov = tilstand.sisteSaksbehandlerSomLøstebehov
        check(
            (sisteLøsteAvklaringsbehov == null && sisteSaksbehandlerSomLøstebehov == null) ||
                (sisteLøsteAvklaringsbehov != null && sisteSaksbehandlerSomLøstebehov != null),
        ) {
            "Siste løste avklaringsbehov og siste saksbehandler som løste behov må enten begge være null eller begge være ikke-null: $sisteLøsteAvklaringsbehov og $sisteSaksbehandlerSomLøstebehov"
        }
    }

    fun id(): BehandlingId = requireNotNull(id) { "Behandling må ha ID" }

    fun leggTilHendelse(hendelse: BehandlingHendelse): Behandling = copy(
        hendelser = hendelser + hendelse,
        oppdatertTidspunkt = hendelse.hendelsesTidspunkt,
        tilstand = hendelse.tilstand,
    )

    fun hendelsesHistorikk(): List<Behandling> = hendelser
        .scan(copy(hendelser = emptyList())) { acc, hendelse ->
            acc.leggTilHendelse(hendelse)
        }.drop(1)

    /**
     * Returnerer behandlingen slik den var på gitt tidspunkt, ved å filtrere
     * hendelser til kun de med hendelsesTidspunkt <= tidspunkt.
     */
    fun påTidspunkt(tidspunkt: LocalDateTime): Behandling {
        val filtrerteHendelser = hendelser.filter { it.hendelsesTidspunkt <= tidspunkt }

        return filtrerteHendelser.fold(this.copy(hendelser = emptyList())) { acc, hendelse ->
            acc.leggTilHendelse(hendelse)
        }
    }

    fun utbetalingId(): String? {
        if (!this.typeBehandling.erYtelsesBehandling) return null

        return when {
            this.resultatFraSisteHendelse() in listOf(
                ResultatKode.TRUKKET,
                ResultatKode.AVSLAG,
                ResultatKode.AVBRUTT,
            ) -> null

            tilstand.status in listOf(
                BehandlingStatus.IVERKSETTES,
                BehandlingStatus.AVSLUTTET,
            ) -> this.referanse.toBase64()

            else -> null
        }
    }

    fun versjon(): String = (hendelser.lastOrNull()?.tilstand ?: tilstand).versjon.verdi

    fun ansvarligBeslutter(): String? =
        hendelser.lastOrNull()?.tilstand?.ansvarligBeslutter ?: tilstand.ansvarligBeslutter

    fun vedtakstidspunkt(): LocalDateTime? =
        hendelser.lastOrNull()?.tilstand?.vedtakstidspunkt ?: tilstand.vedtakstidspunkt

    fun behandlingStatus(): BehandlingStatus = (hendelser.lastOrNull()?.tilstand ?: tilstand).status

    fun avsluttetTid(): LocalDateTime = hendelser.filter { it.tilstand.status == BehandlingStatus.AVSLUTTET }
        .maxOf { it.hendelsesTidspunkt }

    fun oppdatertTidspunkt(): LocalDateTime = hendelser.maxOfOrNull { it.hendelsesTidspunkt }
        ?: requireNotNull(this.oppdatertTidspunkt) { "oppdatertTidspunkt er ikke satt på behandling $referanse." }

    fun resultatFraSisteHendelse(): ResultatKode? = hendelser.lastOrNull()?.tilstand?.resultat

    fun identerPåBehandling(): List<Ident> = listOf(this.sak.person.ident) + this.relaterteIdenter

    fun behandlingMetode(): BehandlingMetode {
        val behov = hendelser.mapNotNull { it.tilstand.gjeldendeAvklaringsBehov }
        val sisteBehov = behov.lastOrNull() ?: return BehandlingMetode.AUTOMATISK

        return when (sisteBehov.tilBehandlingMetode()) {
            BehandlingMetode.KVALITETSSIKRING -> BehandlingMetode.KVALITETSSIKRING
            BehandlingMetode.FATTE_VEDTAK -> BehandlingMetode.FATTE_VEDTAK
            else -> if (behov.any { !it.erAutomatisk() }) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK
        }
    }

    fun nåværendeTilstand(): BehandlingTilstand = tilstand
}

/**
 * @param tidspunkt Tidspunkt for lagring i statistikk-databasen.
 * @param hendelsesTidspunkt Tidspunkt for da hendelsen ble avgitt i behandlingsflyt.
 */
data class BehandlingHendelse(
    val tidspunkt: LocalDateTime?,
    val hendelsesTidspunkt: LocalDateTime,
    val tilstand: BehandlingTilstand,
)

enum class SøknadsFormat {
    PAPIR,
    DIGITAL,
}

enum class BehandlingStatus {
    OPPRETTET,
    UTREDES,
    IVERKSETTES,
    AVSLUTTET,
}

enum class KildeSystem {
    Behandlingsflyt,
    Postmottak,
}

/**
 * Ved oppdateringer her må `kodeverk_behandlingstype`-tabellen oppdateres! Legg til migrering.
 */
enum class TypeBehandling(val kildeSystem: KildeSystem, val erYtelsesBehandling: Boolean = false) {
    Førstegangsbehandling(kildeSystem = KildeSystem.Behandlingsflyt, true),
    Revurdering(kildeSystem = KildeSystem.Behandlingsflyt, true),
    Tilbakekreving(kildeSystem = KildeSystem.Behandlingsflyt, false),
    Klage(kildeSystem = KildeSystem.Behandlingsflyt, false),
    SvarFraAndreinstans(kildeSystem = KildeSystem.Behandlingsflyt),
    Dokumenthåndtering(
        kildeSystem = KildeSystem.Postmottak,
    ),
    Journalføring(kildeSystem = KildeSystem.Postmottak),
    Under18(kildeSystem = KildeSystem.Postmottak),
    Oppfølgingsbehandling(kildeSystem = KildeSystem.Behandlingsflyt),
    Aktivitetsplikt(
        kildeSystem = KildeSystem.Behandlingsflyt,
    ),
    Fordeling(kildeSystem = KildeSystem.Postmottak),

    @Suppress("EnumEntryName")
    Aktivitetsplikt11_9(kildeSystem = KildeSystem.Behandlingsflyt),
}

@Deprecated("Når aarsak_til_opprettelse finnes for alle nye behandlinger, slett denne.")
fun List<Vurderingsbehov>.prioriterÅrsaker(): Vurderingsbehov = this.sortedBy { it.name }.minBy { it.sortering }

fun List<BehandlingHendelse>.ferdigBehandletTid(): LocalDateTime? = this.firstOrNull {
    it.tilstand.status == BehandlingStatus.AVSLUTTET
}?.hendelsesTidspunkt
