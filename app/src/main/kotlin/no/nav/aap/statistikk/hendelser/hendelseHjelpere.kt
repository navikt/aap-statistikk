package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.statistikk.behandling.BehandlingHendelse
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.isBeforeOrEqual
import java.time.LocalDateTime
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as KontraktBehandlingStatus

fun List<AvklaringsbehovHendelseDto>.utledVedtakTid(): LocalDateTime? {
    if (this.any { it.status.returnert() }) return null
    return this.filter { it.avklaringsbehovDefinisjon == Definisjon.FATTE_VEDTAK && it.status == Status.AVSLUTTET }
        .onlyOrNull()?.endringer?.sortedBy { it.tidsstempel }
        ?.lastOrNull { it.status == Status.AVSLUTTET }?.tidsstempel
}

/**
 * Ansvarlig beslutter er saksbehandleren som avsluttet Avklaringsbehov 5099, som
 * er "Fatte vedtak"
 */
fun List<AvklaringsbehovHendelseDto>.utledAnsvarligBeslutter(): String? {
    if (this.any { it.status.returnert() }) return null
    return this.filter { it.avklaringsbehovDefinisjon == Definisjon.FATTE_VEDTAK && it.status == Status.AVSLUTTET }
        .onlyOrNull()?.endringer?.sortedBy { it.tidsstempel }
        ?.lastOrNull { it.status == Status.AVSLUTTET }?.endretAv
}

fun AvklaringsbehovHendelseDto.tidspunktSisteEndring() =
    endringer.maxBy { it.tidsstempel }.tidsstempel

fun List<AvklaringsbehovHendelseDto>.årsakTilRetur(): ÅrsakTilReturKode? {
    return this.filter { it.status.returnert() }
        .minByOrNull { it.tidspunktSisteEndring() }?.endringer?.maxByOrNull { it.tidsstempel }?.årsakTilRetur?.firstOrNull()?.årsak
}

fun List<AvklaringsbehovHendelseDto>.utledBehandlingStatus(): BehandlingStatus {
    val brevSendt =
        this.filter { it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.IVERKSETTES }
            .any { it.status.erAvsluttet() }

    if (brevSendt) {
        return BehandlingStatus.AVSLUTTET
    }

    val brevBehovOpprettet =
        this.filter { it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.IVERKSETTES }
            .any { it.status.erÅpent() }

    if (brevBehovOpprettet) {
        return BehandlingStatus.IVERKSETTES
    }

    val erOpprettet =
        this.filter { it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.OPPRETTET }
            .any { it.status.erÅpent() }

    if (erOpprettet) {
        return BehandlingStatus.OPPRETTET
    }

    return BehandlingStatus.UTREDES
}

fun Status.returnert(): Boolean = when (this) {
    Status.SENDT_TILBAKE_FRA_BESLUTTER, Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> true

    else -> false
}

fun List<AvklaringsbehovHendelseDto>.sistePersonPåBehandling(): String? {
    return this.flatMap { it.endringer }
        .filter { it.endretAv.lowercase() != "Kelvin".lowercase() && it.endretAv.lowercase() != "Brevløsning".lowercase() }
        .maxByOrNull { it.tidsstempel }?.endretAv
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeAvklaringsBehov(): Definisjon? {
    return this.filter {
        !it.avklaringsbehovDefinisjon.erVentebehov()
                && it.status.erÅpent()
    }
        .firstOrNull()?.avklaringsbehovDefinisjon
}

fun List<AvklaringsbehovHendelseDto>.sisteAvklaringsbehovStatus(): Status? {
    return this
        .filter { it.status.erÅpent() && !it.avklaringsbehovDefinisjon.erVentebehov() }
        .firstOrNull()?.status
}

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeStegType(): StegType? {
    return this.filter {
        !it.avklaringsbehovDefinisjon.erVentebehov()
                && it.status.erÅpent()
    }.firstOrNull()?.avklaringsbehovDefinisjon?.løsesISteg
}

fun List<AvklaringsbehovHendelseDto>.utledÅrsakTilSattPåVent(): String? {
    return this.filter { it.status.erÅpent() }
        .filter { it.avklaringsbehovDefinisjon.erVentebehov() && !it.avklaringsbehovDefinisjon.erBrevVentebehov() }
        .flatMap { it.endringer }.maxByOrNull { it.tidsstempel }?.årsakTilSattPåVent?.toString()
}


@JvmName("erAutomatisk")
fun List<BehandlingHendelse>.erManuell(): Boolean {
    return this.filterNot { it.avklaringsBehov == null }.any {
        !Definisjon.forKode(it.avklaringsBehov!!).erAutomatisk()
    }
}

fun List<AvklaringsbehovHendelseDto>.påTidspunkt(tidspunkt: LocalDateTime): List<AvklaringsbehovHendelseDto> {
    return this
        .map {
            val endringer = it.endringer.filter {
                it.tidsstempel.isBeforeOrEqual(tidspunkt)
            }
            it.copy(
                endringer = endringer,
                status = endringer.lastOrNull()?.status ?: it.status
            )
        }
        .filter { it.endringer.isNotEmpty() }
}

fun List<BehandlingHendelse>.ferdigBehandletTid(): LocalDateTime? {
    return this.lastOrNull { it.status == BehandlingStatus.AVSLUTTET }?.hendelsesTidspunkt
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