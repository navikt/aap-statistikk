package no.nav.aap.statistikk.hendelser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BehandlingsflytKontraktTilDomeneTest {
    @Test
    fun `map vurder fritak meldeplikt til meldekort`() {
        val vurderingsbehov = no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov.VURDER_FRITAK_MELDEPLIKT

        assertThat(vurderingsbehov.tilDomene()).isEqualTo(no.nav.aap.statistikk.behandling.Vurderingsbehov.MELDEKORT)
    }

    @Test
    fun `map fastsett arbeidsevne til sykdom arbeidsevne behov for bistand`() {
        val vurderingsbehov = no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov.FASTSETT_ARBEIDSEVNE

        assertThat(vurderingsbehov.tilDomene()).isEqualTo(no.nav.aap.statistikk.behandling.Vurderingsbehov.SYKDOM_ARBEVNE_BEHOV_FOR_BISTAND)
    }
}
