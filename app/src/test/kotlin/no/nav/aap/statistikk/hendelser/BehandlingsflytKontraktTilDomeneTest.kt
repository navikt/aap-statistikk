package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BehandlingsflytKontraktTilDomeneTest {
    @Test
    fun `mapper nye vurderingsbehov til domene`() {
        assertThat(Vurderingsbehov.BRUKER_TILBAKE_I_ARBEID.tilDomene())
            .isEqualTo(no.nav.aap.statistikk.behandling.Vurderingsbehov.BRUKER_TILBAKE_I_ARBEID)
        assertThat(Vurderingsbehov.FERIE_I_SYKEPENGEPERIODE.tilDomene())
            .isEqualTo(no.nav.aap.statistikk.behandling.Vurderingsbehov.FERIE_I_SYKEPENGEPERIODE)
    }
}
