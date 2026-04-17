package no.nav.aap.statistikk.hendelser

import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.jobber.appender.StatistikkHendelse
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.FakeHendelsePublisher
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import no.nav.aap.statistikk.testutils.fakes.FakePersonRepository
import no.nav.aap.statistikk.testutils.fakes.FakeSakRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResendHendelseServiceTest {
    @Test
    fun `beregn historikk, enkelt test`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")
        val behandlingRepository = FakeBehandlingRepository()
        val sakRepository = FakeSakRepository()

        val hendelsePublisher = FakeHendelsePublisher()
        val hendelsesService = ResendHendelseService(
            SakService(sakRepository),
            PersonService(FakePersonRepository()),
            behandlingRepository,
            BehandlingService(behandlingRepository, SkjermingService(FakePdlGateway(emptyMap()))),
            hendelsePublisher
        )

        hendelsesService.prosesserNyHistorikkHendelse(hendelse)

        val behandling = behandlingRepository.hent(hendelse.behandlingReferanse)!!

        assertThat(behandling.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(hendelsePublisher.hendelser).containsExactly(
            StatistikkHendelse.SakstatistikkSkalResendes(behandling.id())
        )
    }

}