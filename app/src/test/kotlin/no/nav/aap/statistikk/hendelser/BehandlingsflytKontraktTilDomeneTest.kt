package no.nav.aap.statistikk.hendelser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BehandlingsflytKontraktTilDomeneTest {
    @Test
    fun `map vurder fritak meldeplikt til same name`() {
        val vurderingsbehov = no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov.VURDER_FRITAK_MELDEPLIKT

        assertThat(vurderingsbehov.tilDomene()).isEqualTo(no.nav.aap.statistikk.behandling.Vurderingsbehov.VURDER_FRITAK_MELDEPLIKT)
    }

    @Test
    fun `map fastsett arbeidsevne til same name`() {
        val vurderingsbehov = no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov.FASTSETT_ARBEIDSEVNE

        assertThat(vurderingsbehov.tilDomene()).isEqualTo(no.nav.aap.statistikk.behandling.Vurderingsbehov.FASTSETT_ARBEIDSEVNE)
    }
}
