package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.api_kontrakt.AvklaringsbehovHendelse
import no.nav.aap.statistikk.api_kontrakt.EndringStatus
import no.nav.aap.statistikk.api_kontrakt.StegType
import java.time.LocalDateTime

fun utledVedtakTid(avklaringsbehov: List<AvklaringsbehovHendelse>): LocalDateTime? {
    return avklaringsbehov
        .filter { it.definisjon.løsesISteg == StegType.FATTE_VEDTAK }
        .firstOrNull { it.status == EndringStatus.AVSLUTTET }
        ?.endringer?.firstOrNull { it.status == EndringStatus.AVSLUTTET }?.tidsstempel
}