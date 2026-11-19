package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.statistikk.behandling.ReturÅrsakkobling
import java.time.LocalDateTime

internal data class AvklaringsbehovTilstand(
    val gjeldendeBehov: GjeldendeBehov?,
    val sisteLøsteBehov: LøstBehov?,
    val saksbehandler: String?,
    val ansvarligBeslutter: String?,
    val venteÅrsak: String?,
    val returÅrsak: String?,
    val returÅrsakkoblinger: List<ReturÅrsakkobling>,
)

internal data class GjeldendeBehov(val definisjon: Definisjon, val status: Status) {
    val steggruppe = definisjon.løsesISteg.gruppe
}

internal data class LøstBehov(val definisjon: Definisjon, val løstAv: String, val tidspunkt: LocalDateTime)

internal fun List<AvklaringsbehovHendelseDto>.utledAvklaringsbehovTilstand(): AvklaringsbehovTilstand {
    val sisteLøsteBehov = utledForrigeLøsteAvklaringsbehov()?.let { (definisjon, løstAv, tidspunkt) ->
        LøstBehov(definisjon, løstAv, tidspunkt)
    }
    val gjeldendeBehov = gjeldendeBehov()?.let {
        GjeldendeBehov(it.avklaringsbehovDefinisjon, it.status)
    }

    return AvklaringsbehovTilstand(
        gjeldendeBehov = gjeldendeBehov,
        sisteLøsteBehov = sisteLøsteBehov,
        saksbehandler = sistePersonPåBehandling(),
        ansvarligBeslutter = utledAnsvarligBeslutter(),
        venteÅrsak = utledÅrsakTilSattPåVent(),
        returÅrsak = årsakTilRetur()?.name,
        returÅrsakkoblinger = returÅrsakkoblinger(),
    )
}
