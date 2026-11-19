package no.nav.aap.statistikk.behandling

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.statistikk.saksstatistikk.BehandlingMetode

internal fun AvklaringsbehovKode.erAutomatisk(): Boolean = Definisjon.forKode(verdi).erAutomatisk()

internal fun AvklaringsbehovKode.erVentebehov(): Boolean = Definisjon.forKode(verdi).erVentebehov()

internal fun AvklaringsbehovKode.løsesAv(): Set<String> = Definisjon.forKode(verdi).løsesAv.map { it.name }.toSet()

internal fun AvklaringsbehovKode.tilBehandlingMetode(): BehandlingMetode =
    when (val definisjon = Definisjon.forKode(verdi)) {
        Definisjon.KVALITETSSIKRING -> BehandlingMetode.KVALITETSSIKRING
        Definisjon.FATTE_VEDTAK -> BehandlingMetode.FATTE_VEDTAK
        else -> if (definisjon.erAutomatisk()) BehandlingMetode.AUTOMATISK else BehandlingMetode.MANUELL
    }
