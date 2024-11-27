package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.AvklaringsbehovKode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import java.time.LocalDateTime
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as EndringStatus


fun List<AvklaringsbehovHendelseDto>.utledVedtakTid(): LocalDateTime? {
    return this
        .filter { it.definisjon.løsesISteg == StegType.FATTE_VEDTAK }
        .firstOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }
        ?.endringer?.firstOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }?.tidsstempel
}

fun List<AvklaringsbehovHendelseDto>.sistePersonPåBehandling(): String? {
    return this.flatMap { it.endringer }
        .filter { it.endretAv.lowercase() != "Kelvin".lowercase() }
        .maxByOrNull { it.tidsstempel }?.endretAv
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeAvklaringsBehov(): String? {
    return this.filter { it.status.erÅpent() }.map { it.definisjon.type }.firstOrNull()?.toString()
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeStegType(): StegType? {
    return this.filter { it.status.erÅpent() }.map { it.definisjon.løsesISteg }.firstOrNull()
}

fun List<AvklaringsbehovHendelseDto>.utledÅrsakTilSattPåVent(): String? {
    return this
        .filter { it.definisjon.behovType == Definisjon.BehovType.VENTEPUNKT }
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
        .filter { it.definisjon.type == AvklaringsbehovKode.`5099` }
        .flatMap { it.endringer }
        .filter { it.status == EndringStatus.AVSLUTTET }
        .map { it.endretAv }
        .firstOrNull()
}

/**
 * Eneste automatiske avklaringsbehov er 9002, "Bestille brev".
 */
fun List<AvklaringsbehovHendelseDto>.erManuell(): Boolean {
    return this.any { it.definisjon.type != AvklaringsbehovKode.`9002` }
}