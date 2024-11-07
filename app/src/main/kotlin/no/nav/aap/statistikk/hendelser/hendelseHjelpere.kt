package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.AvklaringsbehovHendelse
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import java.time.LocalDateTime

fun List<AvklaringsbehovHendelse>.utledVedtakTid(): LocalDateTime? {
    return this
        .filter { it.definisjon.løsesISteg == StegType.FATTE_VEDTAK }
        .firstOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }
        ?.endringer?.firstOrNull { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }?.tidsstempel
}

fun List<AvklaringsbehovHendelse>.sistePersonPåBehandling(): String? {
    return this.flatMap { it.endringer }
        .filter { it.endretAv.lowercase() != "Kelvin".lowercase() }
        .maxByOrNull { it.tidsstempel }?.endretAv
}

fun List<AvklaringsbehovHendelse>.utledGjeldendeAvklaringsBehov(): String? {
    return this.filter { it.status.erÅpent() }.map { it.definisjon.type }.firstOrNull()
}