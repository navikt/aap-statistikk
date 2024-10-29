package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.api_kontrakt.AvklaringsbehovHendelse
import no.nav.aap.statistikk.api_kontrakt.Endring
import no.nav.aap.statistikk.api_kontrakt.EndringStatus
import no.nav.aap.statistikk.api_kontrakt.StegType
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