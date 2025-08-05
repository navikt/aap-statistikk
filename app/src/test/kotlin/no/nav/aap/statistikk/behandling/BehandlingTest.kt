package no.nav.aap.statistikk.behandling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BehandlingTest {

    @Test
    fun `prioriter årsaker til behandling`() {
        val flereÅrsaker = listOf(Vurderingsbehov.SØKNAD, Vurderingsbehov.MELDEKORT)

        assertThat(flereÅrsaker.prioriterÅrsaker()).isEqualTo(Vurderingsbehov.SØKNAD)
    }
}