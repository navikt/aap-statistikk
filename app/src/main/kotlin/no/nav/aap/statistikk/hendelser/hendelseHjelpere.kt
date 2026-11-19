package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.GradBehov
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.ReturÅrsakkobling
import no.nav.aap.statistikk.behandling.tilDomene
import no.nav.aap.statistikk.isBeforeOrEqual
import no.nav.aap.statistikk.onlyOrNull
import java.time.LocalDateTime
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as KontraktBehandlingStatus

fun List<AvklaringsbehovHendelseDto>.utledVedtakTid(): LocalDateTime? {
    // Hvis FATTE_VEDTAK er løst, men det fortsatt finnes åpne behov, så betyr at det
    // det har vært retur fra beslutter.
    val finnesÅpneBehov =
        this.any {
            it.status.erÅpent() &&
                it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.UTREDES &&
                !it.avklaringsbehovDefinisjon.erVentebehov()
        }

    if (finnesÅpneBehov) return null

    return this.filter { it.avklaringsbehovDefinisjon == Definisjon.FATTE_VEDTAK && it.status == Status.AVSLUTTET }
        .onlyOrNull()?.endringer?.sortedBy { it.tidsstempel }
        ?.lastOrNull { it.status == Status.AVSLUTTET }?.tidsstempel
}

/**
 * Ansvarlig beslutter er saksbehandleren som avsluttet Avklaringsbehov 5099, som
 * er "Fatte vedtak"
 */
fun List<AvklaringsbehovHendelseDto>.utledAnsvarligBeslutter(): String? {
    // Hvis FATTE_VEDTAK er løst, men det fortsatt finnes åpne behov, så betyr at det
    // det har vært retur fra beslutter.
    val finnesÅpneBehov =
        this.any {
            it.status.erÅpent() &&
                it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.UTREDES &&
                !it.avklaringsbehovDefinisjon.erVentebehov()
        }

    if (finnesÅpneBehov) return null

    return this.filter { it.avklaringsbehovDefinisjon == Definisjon.FATTE_VEDTAK && it.status == Status.AVSLUTTET }
        .onlyOrNull()?.endringer?.sortedBy { it.tidsstempel }
        ?.lastOrNull { it.status == Status.AVSLUTTET }?.endretAv
}

private fun AvklaringsbehovHendelseDto.tidspunktSisteEndring() = endringer.maxBy { it.tidsstempel }.tidsstempel

fun List<AvklaringsbehovHendelseDto>.tidspunktSisteEndring(): LocalDateTime? =
    this.maxByOrNull { it.tidspunktSisteEndring() }?.tidspunktSisteEndring()

fun List<AvklaringsbehovHendelseDto>.årsakTilRetur(): ÅrsakTilReturKode? = årsakerTilRetur()
    .sortedByDescending { it == ÅrsakTilReturKode.MANGELFULL_BEGRUNNELSE }
    .firstOrNull()

fun List<AvklaringsbehovHendelseDto>.årsakerTilRetur(): List<ÅrsakTilReturKode> {
    val årsaker = this.filter { it.status.returnert() }
        .minByOrNull { it.tidspunktSisteEndring() }
        ?.endringer?.maxByOrNull { it.tidsstempel }
        ?.årsakTilRetur
        .orEmpty()

    return årsaker.map { it.årsak }
}

fun List<AvklaringsbehovHendelseDto>.returÅrsakkoblinger(): List<ReturÅrsakkobling> = this.filter {
    it.status.returnert()
}
    .mapNotNull { avklaringsbehov ->
        val årsaker = avklaringsbehov.endringer
            .maxByOrNull { it.tidsstempel }
            ?.årsakTilRetur
            .orEmpty()
            .map { it.årsak.name }
        if (årsaker.isEmpty()) {
            null
        } else {
            ReturÅrsakkobling(avklaringsbehov.avklaringsbehovDefinisjon, årsaker)
        }
    }

fun List<AvklaringsbehovHendelseDto>.utledBehandlingStatus(): BehandlingStatus {
    val brevSendt =
        this.filter {
            it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.IVERKSETTES ||
                it.avklaringsbehovDefinisjon == Definisjon.SKRIV_BREV
        }
            .any { it.status.erAvsluttet() }

    val brevBehovOpprettet =
        this.filter {
            it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.IVERKSETTES ||
                it.avklaringsbehovDefinisjon == Definisjon.SKRIV_BREV
        }
            .any { it.status.erÅpent() }

    val erOpprettet =
        this.filter { it.avklaringsbehovDefinisjon.løsesISteg.status == KontraktBehandlingStatus.OPPRETTET }
            .any { it.status.erÅpent() }

    return when {
        brevBehovOpprettet -> BehandlingStatus.IVERKSETTES
        brevSendt -> BehandlingStatus.AVSLUTTET
        erOpprettet -> BehandlingStatus.OPPRETTET
        else -> BehandlingStatus.UTREDES
    }
}

fun Status.returnert(): Boolean = tilDomene().returnert()

fun List<AvklaringsbehovHendelseDto>.sistePersonPåBehandling(): String? = this.flatMap { it.endringer }
    .filter {
        it.endretAv.lowercase() != "Kelvin".lowercase() &&
            it.endretAv.lowercase() != "Brevløsning".lowercase()
    }
    .maxByOrNull { it.tidsstempel }?.endretAv

internal fun List<AvklaringsbehovHendelseDto>.gjeldendeBehov(): AvklaringsbehovHendelseDto? =
    filter { it.status.erÅpent() }
        .filterNot { behov ->
            behov.endringer
                .filter { it.gradBehov != null }
                .maxByOrNull { it.tidsstempel }
                ?.gradBehov == GradBehov.FRIVILLIG
        }
        .sortedBy { it.avklaringsbehovDefinisjon.erVentebehov() }
        .firstOrNull()

fun List<AvklaringsbehovHendelseDto>.utledGjeldendeAvklaringsbehov(): Definisjon? =
    gjeldendeBehov()?.avklaringsbehovDefinisjon

fun List<AvklaringsbehovHendelseDto>.utledForrigeLøsteAvklaringsbehov(): Triple<Definisjon, String, LocalDateTime>? =
    this.lastOrNull {
        it.status.erAvsluttet() && it.status != Status.AVBRUTT && !it.avklaringsbehovDefinisjon.erVentebehov() &&
            it.endringer.løstAv() != "Kelvin"
    }?.let {
        val sisteEndring = it.endringer.last()
        Triple(it.avklaringsbehovDefinisjon, sisteEndring.endretAv, sisteEndring.tidsstempel)
    }

fun List<EndringDTO>.løstAv(): String? = this.sortedBy {
    it.tidsstempel
}.lastOrNull { it.status.erAvsluttet() }?.endretAv

fun List<AvklaringsbehovHendelseDto>.sisteAvklaringsbehovStatus(): Status? = gjeldendeBehov()?.status

fun List<AvklaringsbehovHendelseDto>.utledÅrsakTilSattPåVent(): String? = this.filter { it.status.erÅpent() }
    .filter { it.avklaringsbehovDefinisjon.erVentebehov() && !it.avklaringsbehovDefinisjon.erBrevVentebehov() }
    .flatMap { it.endringer }.maxByOrNull { it.tidsstempel }?.årsakTilSattPåVent?.toString()

fun List<AvklaringsbehovHendelseDto>.påTidspunkt(tidspunkt: LocalDateTime): List<AvklaringsbehovHendelseDto> = this
    .map {
        val endringer = it.endringer.filter { endring ->
            endring.tidsstempel.isBeforeOrEqual(tidspunkt)
        }
        it.copy(
            endringer = endringer,
            status = endringer.lastOrNull()?.status ?: it.status,
        )
    }
    .filter { it.endringer.isNotEmpty() }
