package no.nav.aap.statistikk.behandling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BehandlingTest {

    @Test
    fun `prioriter årsaker til behandling`() {
        val flereÅrsaker = listOf(ÅrsakTilBehandling.SØKNAD, ÅrsakTilBehandling.MELDEKORT)

        assertThat(flereÅrsaker.prioriterÅrsaker()).isEqualTo(ÅrsakTilBehandling.SØKNAD)
    }
}