package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.tilgang.Rolle
import java.time.LocalDateTime
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as EndringStatus


fun List<AvklaringsbehovHendelseDto>.utledVedtakTid(): LocalDateTime? {
    return this
        .filter { it.avklaringsbehovDefinisjon.løsesISteg == StegType.FATTE_VEDTAK }
        .firstOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }
        ?.endringer?.sortedBy { it.tidsstempel }
        ?.lastOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }?.tidsstempel
}

fun List<AvklaringsbehovHendelseDto>.utledBrevSendtTid(): LocalDateTime? {
    return this
        .filter { it.avklaringsbehovDefinisjon.løsesISteg == StegType.BREV }
        .firstOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }
        ?.endringer?.sortedBy { it.tidsstempel }
        ?.lastOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }?.tidsstempel
}

fun List<AvklaringsbehovHendelseDto>.sistePersonPåBehandling(): String? {
    return this.flatMap { it.endringer }
        .filter { it.endretAv.lowercase() != "Kelvin".lowercase() }
        .maxByOrNull { it.tidsstempel }?.endretAv
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeAvklaringsBehov(): String? {
    return this
        .filter { it.status.erÅpent() }
        .sortedByDescending {
            it.endringer.minByOrNull { endring -> endring.tidsstempel }!!.tidsstempel
        }
        .map { it.avklaringsbehovDefinisjon.kode }
        .firstOrNull()?.toString()
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeStegType(): StegType? {
    return this
        .filter { it.status.erÅpent() }
        .sortedByDescending {
            it.endringer.minByOrNull { endring -> endring.tidsstempel }!!.tidsstempel
        }
        .map { it.avklaringsbehovDefinisjon.løsesISteg }
        .firstOrNull()
}

fun List<AvklaringsbehovHendelseDto>.utledÅrsakTilSattPåVent(): String? {
    return this
        .filter { it.avklaringsbehovDefinisjon.type == Definisjon.BehovType.VENTEPUNKT }
        .flatMap { it.endringer }
        .maxByOrNull { it.tidsstempel }
        ?.årsakTilSattPåVent?.toString()
}

/**
 * Ansvarlig beslutter er saksbehandleren som avsluttet Avklaringsbehov 5099, som
 * er "Fatte vedtak"
 */
fun List<AvklaringsbehovHendelseDto>.utledAnsvarligBeslutter(): String? {
    return this
        .asSequence()
        .filter { it.avklaringsbehovDefinisjon.kode == AvklaringsbehovKode.`5099` }
        .flatMap { it.endringer }
        .filter { it.status == EndringStatus.AVSLUTTET }
        .map { it.endretAv }
        .firstOrNull()
}

/**
 * Eneste automatiske avklaringsbehov er 9002, "Bestille brev".
 */
@JvmName("erAutomatisk9002")
fun List<AvklaringsbehovHendelseDto>.erManuell(): Boolean {
    return this.any { !it.avklaringsbehovDefinisjon.erAutomatisk() }
}

@JvmName("erAutomatisk")
fun List<BehandlingHendelse>.erManuell(): Boolean {
    return this.any { !it.avklaringsBehov.isNullOrBlank() && !Definisjon.forKode(it.avklaringsBehov).erAutomatisk() }
}
/**
 * Vi utleder at behandlingen er hos NAY om gjeldende avklaringsbehov er et avklaringsbehov som løses av en [no.nav.aap.tilgang.Rolle.SAKSBEHANDLER_OPPFOLGING].
 * Om dette ikke er unikt, se på forrige avklaringsbehov.
 */
fun List<AvklaringsbehovHendelseDto>.hosNAY(): Boolean {
    val nyesteAvklaringsbehov =
        this.filterNot { it.avklaringsbehovDefinisjon.løsesAv.size > 1 }.maxByOrNull {
            it.endringer.minByOrNull { endring -> endring.tidsstempel }!!.tidsstempel
        }

    // Helautomatiske behandlinger velges å skje hos NAY
    if (nyesteAvklaringsbehov == null) return true

    return nyesteAvklaringsbehov.avklaringsbehovDefinisjon.løsesAv.all {
        it in listOf(
            Rolle.SAKSBEHANDLER_NASJONAL,
            Rolle.BESLUTTER,
        )
    }
}

fun erHosNayNy(hendelser: List<BehandlingHendelse>): Boolean {
    return hendelser
            .filterNot { it.avklaringsBehov == null || Definisjon.forKode(it.avklaringsBehov).løsesAv.size > 1 }
            .maxByOrNull { it.tidspunkt }
            ?.let {
                Definisjon.forKode(requireNotNull(it.avklaringsBehov)).løsesAv.all { rolle ->
                    rolle in listOf(
                        Rolle.SAKSBEHANDLER_NASJONAL,
                        Rolle.BESLUTTER
                    )
                }
            } ?: true

}

fun <T> List<T>.only(): T {
    require(this.size == 1) { "Skal ha lengde én, men har lengde ${this.size}: ${this.joinToString(",")}" }
    return this.first()
}

fun <T> List<T>.onlyOrNull(): T? {
    require(this.size <= 1) {
        "Skal ha lengde maks én, men har lengde ${this.size}: ${
            this.joinToString(
                ","
            )
        }"
    }
    return this.firstOrNull()
}