package no.nav.aap.statistikk.hendelser

import io.mockk.mockk
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.testutils.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.FakePersonRepository
import no.nav.aap.statistikk.testutils.FakeSakRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResendHendelseServiceTest {
    @Test
    fun `beregn historikk, enkelt test`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")
        val behandlingRepository = FakeBehandlingRepository()
        val sakRepository = FakeSakRepository()

        val opprettBigQueryLagringCallback = mockk<(BehandlingId) -> Unit>(relaxed = true)
        val hendelsesService = ResendHendelseService(
            SakService(sakRepository),
            PersonService(FakePersonRepository()),
            behandlingRepository,
            BehandlingService(behandlingRepository),
            opprettBigQueryLagringCallback
        )

        hendelsesService.prosesserNyHistorikkHendelse(hendelse)

        val behandling = behandlingRepository.hent(hendelse.behandlingReferanse)!!

        assertThat(behandling.status).isEqualTo(BehandlingStatus.AVSLUTTET)
    }

}