package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.hendelser.erManuell
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.utbetaling.helved.toBase64
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.contains
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus

data class Versjon(
    val verdi: String,
    val id: Long? = null,
)

private val log = LoggerFactory.getLogger(Behandling::class.java)

/**
 * @param versjon Applikasjonsversjon fra behandlingsflyt på denne behandlingen.
 */
data class Behandling(
    val id: BehandlingId? = null,
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
    val relatertBehandlingId: BehandlingId? = null,
    val snapShotId: Long? = null,
    val gjeldendeAvklaringsBehov: String? = null,
    val gjeldendeAvklaringsbehovStatus: AvklaringsbehovStatus? = null,
    val venteÅrsak: String? = null,
    val returÅrsak: String? = null,
    val gjeldendeStegGruppe: StegGruppe? = null,
    val årsaker: List<Vurderingsbehov> = listOf(),
    val resultat: ResultatKode? = null,
    val oppdatertTidspunkt: LocalDateTime? = LocalDateTime.now(),
    val hendelser: List<BehandlingHendelse> = listOf(),
) {
    init {
        if (typeBehandling == TypeBehandling.Førstegangsbehandling) {
            require((ansvarligBeslutter != null && vedtakstidspunkt != null) || (ansvarligBeslutter == null && vedtakstidspunkt == null)) {
                "Om behandlingen er besluttet, så må både vedtakstidspunkt og ansvarlig beslutter være ikke-null. Har ansvarlig beslutter: $ansvarligBeslutter, har vedtakstidspunkt: $vedtakstidspunkt"
            }
        }

        require(hendelser.sortedBy { it.hendelsesTidspunkt }
            .zipWithNext { a, b -> a.hendelsesTidspunkt <= b.hendelsesTidspunkt }
            .all { it }) { "Hendelser må være sortert." }
    }

    fun leggTilHendelse(hendelse: BehandlingHendelse): Behandling {
        return this.copy(
            hendelser = this.hendelser + hendelse,
            oppdatertTidspunkt = hendelse.hendelsesTidspunkt,
            versjon = hendelse.versjon,
            gjeldendeAvklaringsBehov = hendelse.avklaringsBehov,
            gjeldendeAvklaringsbehovStatus = hendelse.avklaringsbehovStatus,
            mottattTid = hendelse.mottattTid,
            vedtakstidspunkt = hendelse.vedtakstidspunkt,
            gjeldendeStegGruppe = hendelse.steggruppe,
            ansvarligBeslutter = hendelse.ansvarligBeslutter,
            status = hendelse.status,
            venteÅrsak = hendelse.venteÅrsak,
            returÅrsak = hendelse.returÅrsak,
            sisteSaksbehandler = hendelse.saksbehandler?.ident,
            søknadsformat = hendelse.søknadsformat,
            resultat = hendelse.resultat,
        )
    }

    fun hendelsesHistorikk(): List<Behandling> {
        return (1..<this.hendelser.size + 1).map { this.hendelser.subList(0, it) }
            .scan(this.copy(hendelser = emptyList())) { acc, hendelser ->
                acc.leggTilHendelse(hendelser.last())
            }.drop(1)
    }

    fun utbetalingId(): String? {
        if (this.resultat() in listOf(
                ResultatKode.TRUKKET, ResultatKode.AVSLAG, ResultatKode.AVBRUTT
            )
        ) return null

        if (this.status in listOf(BehandlingStatus.IVERKSETTES, BehandlingStatus.AVSLUTTET)) {
            return this.referanse.toBase64()
        }
        return null
    }

    fun behandlingStatus(): BehandlingStatus {
        return hendelser.lastOrNull()?.status ?: this.status
    }

    fun avsluttetTid(): LocalDateTime {
        return hendelser.filter { it.status == BehandlingStatus.AVSLUTTET }
            .maxOf { it.hendelsesTidspunkt }
    }

    fun resultat(): ResultatKode? {
        return hendelser.lastOrNull()?.resultat
    }

    fun identerPåBehandling(): List<String> {
        return listOf(this.sak.person.ident) + this.relaterteIdenter
    }

    fun behandlingMetode(): BehandlingMetode {
        if (this.hendelser.isEmpty()) {
            log.info("Behandling-hendelser var tom. Behandling: ${this.referanse}")
            return BehandlingMetode.AUTOMATISK
        }
        val sisteHendelse = this.hendelser.last()
        if (sisteHendelse.avklaringsBehov.isNullOrBlank()) {
            log.info("Ingen avkl.funnet for siste hendelse. Behandling: ${this.referanse}. Antall hendelser: ${this.hendelser.size}")
            return this.copy(hendelser = this.hendelser.dropLast(1)).behandlingMetode()
        }

        val sisteDefinisjon = Definisjon.forKode(sisteHendelse.avklaringsBehov)

        if (sisteDefinisjon == Definisjon.KVALITETSSIKRING) {
            return BehandlingMetode.KVALITETSSIKRING
        }

        if (sisteDefinisjon == Definisjon.FATTE_VEDTAK) {
            return BehandlingMetode.FATTE_VEDTAK
        }

        if (!this.hendelser.erManuell()) {
            log.info("Hendelser: $this")
        }

        return if (this.hendelser.erManuell()) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK
    }
}

/**
 * @param tidspunkt Tidspunkt for lagring i statistikk-databasen.
 * @param hendelsesTidspunkt Tidspunkt for da hendelsen ble avgitt i behandlingsflyt.
 */
data class BehandlingHendelse(
    val tidspunkt: LocalDateTime?,
    val hendelsesTidspunkt: LocalDateTime,
    val avklaringsBehov: String? = null,
    val steggruppe: StegGruppe? = null,
    val avklaringsbehovStatus: AvklaringsbehovStatus?,
    val venteÅrsak: String? = null,
    val returÅrsak: String? = null,
    val saksbehandler: Saksbehandler? = null,
    val resultat: ResultatKode? = null,
    val versjon: Versjon,
    val status: BehandlingStatus,
    val ansvarligBeslutter: String? = null,
    val vedtakstidspunkt: LocalDateTime? = null,
    val mottattTid: LocalDateTime,
    val søknadsformat: SøknadsFormat,
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

/**
 * Ved oppdateringer her må `kodeverk_behandlingstype`-tabellen oppdateres! Legg til migrering.
 */
enum class TypeBehandling(val kildeSystem: KildeSystem) {
    Førstegangsbehandling(kildeSystem = KildeSystem.Behandlingsflyt), Revurdering(kildeSystem = KildeSystem.Behandlingsflyt), Tilbakekreving(
        kildeSystem = KildeSystem.Behandlingsflyt
    ),
    Klage(kildeSystem = KildeSystem.Behandlingsflyt), SvarFraAndreinstans(kildeSystem = KildeSystem.Behandlingsflyt), Dokumenthåndtering(
        kildeSystem = KildeSystem.Postmottak
    ),
    Journalføring(kildeSystem = KildeSystem.Postmottak), Oppfølgingsbehandling(kildeSystem = KildeSystem.Behandlingsflyt), Aktivitetsplikt(
        kildeSystem = KildeSystem.Behandlingsflyt
    ),
    Aktivitetsplikt11_9(kildeSystem = KildeSystem.Behandlingsflyt),
}

enum class Vurderingsbehov(val sortering: Int) {
    SØKNAD(0), HELHETLIG_VURDERING(1), AKTIVITETSMELDING(1), MELDEKORT(1), FRITAK_MELDEPLIKT(2), LEGEERKLÆRING(
        1
    ),
    AVVIST_LEGEERKLÆRING(1), DIALOGMELDING(1), G_REGULERING(1), REVURDER_MEDLEMSSKAP(1), REVURDER_YRKESSKADE(
        1
    ),
    REVURDER_BEREGNING(1), REVURDER_LOVVALG(1), REVURDER_MELDEPLIKT_RIMELIG_GRUNN(1), KLAGE(0), REVURDER_SAMORDNING(
        1
    ),
    LOVVALG_OG_MEDLEMSKAP(1), FORUTGAENDE_MEDLEMSKAP(1), SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND(1), BARNETILLEGG(
        1
    ),
    INSTITUSJONSOPPHOLD(1), SAMORDNING_OG_AVREGNING(1), REFUSJONSKRAV(1), UTENLANDSOPPHOLD_FOR_SOKNADSTIDSPUNKT(
        1
    ),
    VURDER_RETTIGHETSPERIODE(1), SØKNAD_TRUKKET(0), REVURDER_MANUELL_INNTEKT(1), KLAGE_TRUKKET(1), MOTTATT_KABAL_HENDELSE(
        1
    ),
    OPPFØLGINGSOPPGAVE(0), AKTIVITETSPLIKT_11_7(1), AKTIVITETSPLIKT_11_9(1), EFFEKTUER_AKTIVITETSPLIKT(
        1
    ),
    OVERGANG_UFORE(1), OVERGANG_ARBEID(1), DØDSFALL_BRUKER(1), DØDSFALL_BARN(1), REVURDERING_AVBRUTT(
        0
    );
}


fun List<Vurderingsbehov>.prioriterÅrsaker(): Vurderingsbehov {
    return this.minByOrNull { it.sortering }!!
}

