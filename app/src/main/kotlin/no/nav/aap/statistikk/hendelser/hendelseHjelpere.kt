package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.AvklaringsbehovHendelse
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.EndringStatus
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import java.time.LocalDateTime

fun List<AvklaringsbehovHendelse>.utledVedtakTid(): LocalDateTime? {
    return this
        .filter { it.definisjon.løsesISteg == StegType.FATTE_VEDTAK }
        .firstOrNull { it.status == EndringStatus.AVSLUTTET }
        ?.endringer?.firstOrNull { it.status == EndringStatus.AVSLUTTET }?.tidsstempel
}

fun List<AvklaringsbehovHendelse>.sistePersonPåBehandling(): String? {
    return this.flatMap { it.endringer }
        .filter { it.endretAv.lowercase() != "Kelvin".lowercase() }
        .maxByOrNull { it.tidsstempel }?.endretAv
}

fun List<AvklaringsbehovHendelse>.utledGjeldendeAvklaringsBehov(): String? {
    return this.filter { it.status.erÅpent() }.map { it.definisjon.type }.firstOrNull()
}

fun EndringStatus.erÅpent(): Boolean {
    return this in setOf(
        EndringStatus.OPPRETTET,
        EndringStatus.SENDT_TILBAKE_FRA_BESLUTTER,
        EndringStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER
    )
}