package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegGruppe
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode
import no.nav.aap.utbetaling.helved.toBase64
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus

data class Versjon(
    val verdi: String,
    val id: Long? = null,
)

private val log = LoggerFactory.getLogger(Behandling::class.java)

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
    val relaterteIdenter: List<String> = listOf(),
    val relatertBehandlingId: BehandlingId? = null,
    val relatertBehandlingReferanse: String? = null,
    val snapShotId: Long? = null,
    val gjeldendeAvklaringsBehov: String? = null,
    val gjeldendeAvklaringsbehovStatus: AvklaringsbehovStatus? = null,
    val sisteLøsteAvklaringsbehov: String? = null,
    val sisteSaksbehandlerSomLøstebehov: String? = null,
    val venteÅrsak: String? = null,
    val returÅrsak: String? = null,
    val gjeldendeStegGruppe: StegGruppe? = null,
    val årsaker: List<Vurderingsbehov> = listOf(),
    val årsakTilOpprettelse: String? = null,
    val resultat: ResultatKode? = null,
    private val oppdatertTidspunkt: LocalDateTime? = LocalDateTime.now(),
    val hendelser: List<BehandlingHendelse> = listOf(),
    val opprettetAv: String? = null,
) {
    init {
        require(hendelser.sortedBy { it.hendelsesTidspunkt }
            .zipWithNext { a, b -> a.hendelsesTidspunkt <= b.hendelsesTidspunkt }
            .all { it }) { "Hendelser må være sortert." }

        check(
            (sisteLøsteAvklaringsbehov == null && sisteSaksbehandlerSomLøstebehov == null)
                    || (sisteLøsteAvklaringsbehov != null && sisteSaksbehandlerSomLøstebehov != null)
        )
    }

    fun id(): BehandlingId {
        return requireNotNull(id) { "Behandling må ha ID" }
    }

    fun leggTilHendelse(hendelse: BehandlingHendelse): Behandling {
        return this.copy(
            hendelser = this.hendelser + hendelse,
            oppdatertTidspunkt = hendelse.hendelsesTidspunkt,
            versjon = hendelse.versjon,
            gjeldendeAvklaringsBehov = hendelse.avklaringsBehov,
            gjeldendeAvklaringsbehovStatus = hendelse.avklaringsbehovStatus,
            sisteLøsteAvklaringsbehov = hendelse.sisteLøsteAvklaringsbehov,
            sisteSaksbehandlerSomLøstebehov = hendelse.sisteSaksbehandlerSomLøstebehov,
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
            relatertBehandlingReferanse = hendelse.relatertBehandlingReferanse,
        )
    }

    fun hendelsesHistorikk(): List<Behandling> {
        return (1..<this.hendelser.size + 1).map { this.hendelser.subList(0, it) }
            .scan(this.copy(hendelser = emptyList())) { acc, hendelser ->
                acc.leggTilHendelse(hendelser.last())
            }.drop(1)
    }

    fun utbetalingId(): String? {
        return when {
            this.resultat() in listOf(
                ResultatKode.TRUKKET, ResultatKode.AVSLAG, ResultatKode.AVBRUTT
            ) -> null

            this.status in listOf(
                BehandlingStatus.IVERKSETTES, BehandlingStatus.AVSLUTTET
            ) -> this.referanse.toBase64()

            else -> null
        }
    }

    fun behandlingStatus(): BehandlingStatus {
        return hendelser.lastOrNull()?.status ?: this.status
    }

    fun avsluttetTid(): LocalDateTime {
        return hendelser.filter { it.status == BehandlingStatus.AVSLUTTET }
            .maxOf { it.hendelsesTidspunkt }
    }

    fun oppdatertTidspunkt(): LocalDateTime {
        return hendelser.maxOfOrNull { it.hendelsesTidspunkt }
            ?: requireNotNull(this.oppdatertTidspunkt) { "oppdatertTidspunkt er ikke satt på behandling $referanse." }
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

@JvmName("erAutomatisk")
fun List<BehandlingHendelse>.erManuell(): Boolean {
    return this.filterNot { it.avklaringsBehov == null }.any {
        !Definisjon.forKode(requireNotNull(it.avklaringsBehov)).erAutomatisk()
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
    val sisteLøsteAvklaringsbehov: String? = null,
    val sisteSaksbehandlerSomLøstebehov: String? = null,
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
    val relatertBehandlingReferanse: String?
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

    @Suppress("EnumEntryName")
    Aktivitetsplikt11_9(kildeSystem = KildeSystem.Behandlingsflyt),
}

@Deprecated("Når aarsak_til_opprettelse finnes for alle nye behandlinger, slett denne.")
fun List<Vurderingsbehov>.prioriterÅrsaker(): Vurderingsbehov {
    return this.sortedBy { it.name }.minBy { it.sortering }
}

