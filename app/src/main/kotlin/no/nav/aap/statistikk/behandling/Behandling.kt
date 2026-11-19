package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.utbetaling.helved.toBase64
import java.time.LocalDateTime
import java.util.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus

@JvmInline
value class Versjon(val verdi: String)

/**
 * @param versjon Applikasjonsversjon fra behandlingsflyt på denne behandlingen.
 * @param relatertBehandlingId Referer til en relatert Kelvin-behandling.
 * @param relatertBehandlingReferanse Referer til en relatert behandling som ikke nødvendigvis er en Kelvin-behandling.
 */
data class Behandling(
    val id: BehandlingId? = null,
    val referanse: UUID,
    val sak: Sak,
    val typeBehandling: TypeBehandling,
    private val status: BehandlingStatus,
    val opprettetTid: LocalDateTime,
    val mottattTid: LocalDateTime,
    val vedtakstidspunkt: LocalDateTime? = null,
    val ansvarligBeslutter: String? = null,
    val versjon: Versjon,
    val søknadsformat: SøknadsFormat,
    val sisteSaksbehandler: String? = null,
    val relaterteIdenter: List<Ident> = listOf(),
    val relatertBehandlingId: BehandlingId? = null,
    val relatertBehandlingReferanse: String? = null,
    val snapShotId: Long? = null,
    val gjeldendeAvklaringsBehov: Definisjon? = null,
    val gjeldendeAvklaringsbehovStatus: AvklaringsbehovStatus? = null,
    val sisteLøsteAvklaringsbehov: Definisjon? = null,
    val sisteSaksbehandlerSomLøstebehov: String? = null,
    val sistLøsteAvklaringsbehovTidspunkt: LocalDateTime? = null,
    val venteÅrsak: String? = null,
    val returÅrsak: String? = null,
    val returÅrsakkoblinger: List<ReturÅrsakkobling> = listOf(),
    val gjeldendeStegGruppe: StegGruppe? = null,
    val årsaker: List<Vurderingsbehov> = listOf(),
    val årsakTilOpprettelse: String? = null,
    val resultat: ResultatKode? = null,
    private val oppdatertTidspunkt: LocalDateTime? = null,
    val hendelser: List<BehandlingHendelse> = listOf(),
    val opprettetAv: String? = null,
) {
    init {
        require(
            hendelser.sortedBy { it.hendelsesTidspunkt }
                .zipWithNext { a, b -> a.hendelsesTidspunkt <= b.hendelsesTidspunkt }
                .all { it },
        ) { "Hendelser må være sortert." }

        check(
            (sisteLøsteAvklaringsbehov == null && sisteSaksbehandlerSomLøstebehov == null) ||
                (sisteLøsteAvklaringsbehov != null && sisteSaksbehandlerSomLøstebehov != null),
        ) {
            "Siste løste avklaringsbehov og siste saksbehandler som løste behov må enten begge være null eller begge være ikke-null: $sisteLøsteAvklaringsbehov og $sisteSaksbehandlerSomLøstebehov"
        }
    }

    fun id(): BehandlingId = requireNotNull(id) { "Behandling må ha ID" }

    fun leggTilHendelse(hendelse: BehandlingHendelse): Behandling = this.copy(
        hendelser = this.hendelser + hendelse,
        oppdatertTidspunkt = hendelse.hendelsesTidspunkt,
        versjon = hendelse.versjon,
        gjeldendeAvklaringsBehov = hendelse.avklaringsBehov,
        gjeldendeAvklaringsbehovStatus = hendelse.avklaringsbehovStatus,
        sisteLøsteAvklaringsbehov = hendelse.sisteLøsteAvklaringsbehov,
        sisteSaksbehandlerSomLøstebehov = hendelse.sisteSaksbehandlerSomLøstebehov,
        sistLøsteAvklaringsbehovTidspunkt = hendelse.sistLøsteAvklaringsbehovTidspunkt,
        mottattTid = hendelse.mottattTid,
        vedtakstidspunkt = hendelse.vedtakstidspunkt,
        gjeldendeStegGruppe = hendelse.steggruppe,
        ansvarligBeslutter = hendelse.ansvarligBeslutter,
        status = hendelse.status,
        venteÅrsak = hendelse.venteÅrsak,
        returÅrsak = hendelse.returÅrsak,
        returÅrsakkoblinger = hendelse.returÅrsakkoblinger,
        sisteSaksbehandler = hendelse.saksbehandler?.ident,
        søknadsformat = hendelse.søknadsformat,
        resultat = hendelse.resultat,
        relatertBehandlingReferanse = hendelse.relatertBehandlingReferanse,
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

            nåværendeTilstand().status in listOf(
                BehandlingStatus.IVERKSETTES,
                BehandlingStatus.AVSLUTTET,
            ) -> this.referanse.toBase64()

            else -> null
        }
    }

    fun versjon(): String = (hendelser.lastOrNull()?.tilTilstand() ?: nåværendeTilstand()).versjon.verdi

    fun ansvarligBeslutter(): String? =
        hendelser.lastOrNull()?.tilTilstand()?.ansvarligBeslutter ?: nåværendeTilstand().ansvarligBeslutter

    fun vedtakstidspunkt(): LocalDateTime? =
        hendelser.lastOrNull()?.tilTilstand()?.vedtakstidspunkt ?: nåværendeTilstand().vedtakstidspunkt

    fun behandlingStatus(): BehandlingStatus = (hendelser.lastOrNull()?.tilTilstand() ?: nåværendeTilstand()).status

    fun avsluttetTid(): LocalDateTime = hendelser.filter { it.tilTilstand().status == BehandlingStatus.AVSLUTTET }
        .maxOf { it.hendelsesTidspunkt }

    fun oppdatertTidspunkt(): LocalDateTime = hendelser.maxOfOrNull { it.hendelsesTidspunkt }
        ?: requireNotNull(this.oppdatertTidspunkt) { "oppdatertTidspunkt er ikke satt på behandling $referanse." }

    fun resultatFraSisteHendelse(): ResultatKode? = hendelser.lastOrNull()?.tilTilstand()?.resultat

    fun identerPåBehandling(): List<Ident> = listOf(this.sak.person.ident) + this.relaterteIdenter

    fun behandlingMetode(): BehandlingMetode {
        val behov = hendelser.mapNotNull { it.tilTilstand().gjeldendeAvklaringsBehov }
        val sisteBehov = behov.lastOrNull() ?: return BehandlingMetode.AUTOMATISK

        return when (sisteBehov.tilBehandlingMetode()) {
            BehandlingMetode.KVALITETSSIKRING -> BehandlingMetode.KVALITETSSIKRING
            BehandlingMetode.FATTE_VEDTAK -> BehandlingMetode.FATTE_VEDTAK
            else -> if (behov.any { !it.erAutomatisk() }) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK
        }
    }

    fun nåværendeTilstand(): BehandlingTilstand = BehandlingTilstand(
        status = status,
        versjon = versjon,
        mottattTid = mottattTid,
        søknadsformat = søknadsformat,
        ansvarligBeslutter = ansvarligBeslutter,
        vedtakstidspunkt = vedtakstidspunkt,
        sisteSaksbehandler = sisteSaksbehandler,
        resultat = resultat,
        gjeldendeAvklaringsBehov = gjeldendeAvklaringsBehov?.tilAvklaringsbehovKode(),
        gjeldendeAvklaringsbehovStatus = gjeldendeAvklaringsbehovStatus?.tilDomene(),
        sisteLøsteAvklaringsbehov = sisteLøsteAvklaringsbehov?.tilAvklaringsbehovKode(),
        sisteSaksbehandlerSomLøstebehov = sisteSaksbehandlerSomLøstebehov,
        sistLøsteAvklaringsbehovTidspunkt = sistLøsteAvklaringsbehovTidspunkt,
        gjeldendeStegGruppe = gjeldendeStegGruppe?.tilDomene(),
        venteÅrsak = venteÅrsak,
        returÅrsak = returÅrsak,
        returÅrsakkoblinger = returÅrsakkoblinger.map { it.tilTilstand() },
        relatertBehandlingReferanse = relatertBehandlingReferanse,
    )

    // Overgang til felles tilstand; gamle lesere bruker fortsatt de enkelte feltene.
    constructor(
        referanse: UUID,
        sak: Sak,
        typeBehandling: TypeBehandling,
        opprettetTid: LocalDateTime,
        tilstand: BehandlingTilstand,
        id: BehandlingId? = null,
        relaterteIdenter: List<Ident> = emptyList(),
        relatertBehandlingId: BehandlingId? = null,
        snapShotId: Long? = null,
        årsaker: List<Vurderingsbehov> = emptyList(),
        årsakTilOpprettelse: String? = null,
        oppdatertTidspunkt: LocalDateTime? = null,
        hendelser: List<BehandlingHendelse> = emptyList(),
        opprettetAv: String? = null,
    ) : this(
        id = id,
        referanse = referanse,
        sak = sak,
        typeBehandling = typeBehandling,
        opprettetTid = opprettetTid,
        relaterteIdenter = relaterteIdenter,
        relatertBehandlingId = relatertBehandlingId,
        snapShotId = snapShotId,
        årsaker = årsaker,
        årsakTilOpprettelse = årsakTilOpprettelse,
        oppdatertTidspunkt = oppdatertTidspunkt,
        hendelser = hendelser,
        opprettetAv = opprettetAv,
        status = tilstand.status,
        versjon = tilstand.versjon,
        mottattTid = tilstand.mottattTid,
        søknadsformat = tilstand.søknadsformat,
        ansvarligBeslutter = tilstand.ansvarligBeslutter,
        vedtakstidspunkt = tilstand.vedtakstidspunkt,
        sisteSaksbehandler = tilstand.sisteSaksbehandler,
        resultat = tilstand.resultat,
        gjeldendeAvklaringsBehov = tilstand.gjeldendeAvklaringsBehov?.tilDefinisjon(),
        gjeldendeAvklaringsbehovStatus = tilstand.gjeldendeAvklaringsbehovStatus?.tilKontrakt(),
        sisteLøsteAvklaringsbehov = tilstand.sisteLøsteAvklaringsbehov?.tilDefinisjon(),
        sisteSaksbehandlerSomLøstebehov = tilstand.sisteSaksbehandlerSomLøstebehov,
        sistLøsteAvklaringsbehovTidspunkt = tilstand.sistLøsteAvklaringsbehovTidspunkt,
        gjeldendeStegGruppe = tilstand.gjeldendeStegGruppe?.tilKontrakt(),
        venteÅrsak = tilstand.venteÅrsak,
        returÅrsak = tilstand.returÅrsak,
        returÅrsakkoblinger = tilstand.returÅrsakkoblinger.map { it.tilReturÅrsakkobling() },
        relatertBehandlingReferanse = tilstand.relatertBehandlingReferanse,
    )
}

/**
 * @param tidspunkt Tidspunkt for lagring i statistikk-databasen.
 * @param hendelsesTidspunkt Tidspunkt for da hendelsen ble avgitt i behandlingsflyt.
 */
data class BehandlingHendelse(
    val tidspunkt: LocalDateTime?,
    val hendelsesTidspunkt: LocalDateTime,
    val avklaringsBehov: Definisjon? = null,
    val sisteLøsteAvklaringsbehov: Definisjon? = null,
    val sisteSaksbehandlerSomLøstebehov: String? = null,
    val sistLøsteAvklaringsbehovTidspunkt: LocalDateTime? = null,
    val steggruppe: StegGruppe? = null,
    val avklaringsbehovStatus: AvklaringsbehovStatus?,
    val venteÅrsak: String? = null,
    val returÅrsak: String? = null,
    val returÅrsakkoblinger: List<ReturÅrsakkobling> = listOf(),
    val saksbehandler: Saksbehandler? = null,
    val resultat: ResultatKode? = null,
    val versjon: Versjon,
    val status: BehandlingStatus,
    val ansvarligBeslutter: String? = null,
    val vedtakstidspunkt: LocalDateTime? = null,
    val mottattTid: LocalDateTime,
    val søknadsformat: SøknadsFormat,
    val relatertBehandlingReferanse: String?,
) {
    constructor(
        tidspunkt: LocalDateTime?,
        hendelsesTidspunkt: LocalDateTime,
        tilstand: BehandlingTilstand,
    ) : this(
        tidspunkt = tidspunkt,
        hendelsesTidspunkt = hendelsesTidspunkt,
        status = tilstand.status,
        versjon = tilstand.versjon,
        mottattTid = tilstand.mottattTid,
        søknadsformat = tilstand.søknadsformat,
        ansvarligBeslutter = tilstand.ansvarligBeslutter,
        vedtakstidspunkt = tilstand.vedtakstidspunkt,
        saksbehandler = tilstand.sisteSaksbehandler?.let(::Saksbehandler),
        resultat = tilstand.resultat,
        avklaringsBehov = tilstand.gjeldendeAvklaringsBehov?.tilDefinisjon(),
        avklaringsbehovStatus = tilstand.gjeldendeAvklaringsbehovStatus?.tilKontrakt(),
        sisteLøsteAvklaringsbehov = tilstand.sisteLøsteAvklaringsbehov?.tilDefinisjon(),
        sisteSaksbehandlerSomLøstebehov = tilstand.sisteSaksbehandlerSomLøstebehov,
        sistLøsteAvklaringsbehovTidspunkt = tilstand.sistLøsteAvklaringsbehovTidspunkt,
        steggruppe = tilstand.gjeldendeStegGruppe?.tilKontrakt(),
        venteÅrsak = tilstand.venteÅrsak,
        returÅrsak = tilstand.returÅrsak,
        returÅrsakkoblinger = tilstand.returÅrsakkoblinger.map { it.tilReturÅrsakkobling() },
        relatertBehandlingReferanse = tilstand.relatertBehandlingReferanse,
    )
}

data class ReturÅrsakkobling(val avklaringsbehov: Definisjon, val returÅrsaker: List<String>)

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
    it.tilTilstand().status == BehandlingStatus.AVSLUTTET
}?.hendelsesTidspunkt
