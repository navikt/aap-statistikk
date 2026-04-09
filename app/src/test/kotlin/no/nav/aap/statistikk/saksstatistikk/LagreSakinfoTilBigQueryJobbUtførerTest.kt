package no.nav.aap.statistikk.saksstatistikk

import io.mockk.every
import io.mockk.mockk
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.testutils.MockJobbAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LagreSakinfoTilBigQueryJobbUtførerTest {

    private val testConfig = EnhetRetryConfig(maxRetries = 3, delaySeconds = 60)
    private val fakeRepositoryProvider = mockk<RepositoryProvider>()

    @BeforeEach
    fun settOppStandardMocks() {
        every { fakeRepositoryProvider.provide<BehandlingRepository>() } returns mockk(relaxed = true)
    }

    private fun lagUtfører(
        service: ISaksStatistikkService,
        jobbAppender: MockJobbAppender,
        config: EnhetRetryConfig = testConfig
    ) = LagreSakinfoTilBigQueryJobbUtfører(service, jobbAppender, fakeRepositoryProvider, config)

    @Test
    fun `lagrer normalt når enhet finnes`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(behandlingId)
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(0)
    }

    @Test
    fun `reschedulerer når enhet mangler ved første forsøk`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM")
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(behandlingId)
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallLagreUtenEnhet).isFalse()
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(1)
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(testConfig.delaySeconds)
    }

    @Test
    fun `delay er eksponentielt basert på retry-teller`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM")
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        // retryCount=0 → delay = 60 * 2^0 = 60
        utfører.utfør(JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(behandlingId))
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(60L)

        // retryCount=1 → delay = 60 * 2^1 = 120
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(behandlingId)
                .medParameter("enhetRetryCount", "1")
        )
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(120L)

        // retryCount=2 → delay = 60 * 2^2 = 240
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(behandlingId)
                .medParameter("enhetRetryCount", "2")
        )
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(240L)
    }

    @Test
    fun `reschedulerer med økt retry-teller`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM")
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("enhetRetryCount", "1")
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallLagreUtenEnhet).isFalse()
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(2)
    }

    @Test
    fun `lagrer med null enhet etter maks antall forsøk`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM")
        )
        val jobbAppender = MockJobbAppender()
        val fakeBehandlingRepository = mockk<BehandlingRepository>(relaxed = true) {
            every { hent(behandlingId) } returns mockk { every { referanse } returns UUID.randomUUID() }
        }
        every { fakeRepositoryProvider.provide<BehandlingRepository>() } returns fakeBehandlingRepository

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("enhetRetryCount", testConfig.maxRetries.toString())
        utfører.utfør(input)

        // Første kall returnerer ManglerEnhet, andre kall med lagreUtenEnhet=true
        assertThat(service.kallteller).isEqualTo(2)
        assertThat(service.sisteKallLagreUtenEnhet).isTrue()
    }

    @Test
    fun `config kan overstyres i test`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM")
        )
        val jobbAppender = MockJobbAppender()
        val customConfig = EnhetRetryConfig(maxRetries = 1, delaySeconds = 10)

        val utfører = lagUtfører(service, jobbAppender, config = customConfig)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(behandlingId)
        utfører.utfør(input)

        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(10)
    }

    @Test
    fun `ManglerEnhet inneholder avklaringsbehovkode`() {
        val behandlingId = BehandlingId(1)
        val avklaringsbehovKode = "AVKLAR_SYKDOM"
        val resultat = SakStatistikkResultat.ManglerEnhet(behandlingId, avklaringsbehovKode)

        assertThat(resultat.avklaringsbehovKode).isEqualTo(avklaringsbehovKode)
        assertThat(resultat.behandlingId).isEqualTo(behandlingId)
    }

    @Test
    fun `ved retry brukes lagreSakInfoTilBigquery`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("enhetRetryCount", "1")
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
    }
}

private class FakeSaksStatistikkService(
    private val resultat: SakStatistikkResultat
) : ISaksStatistikkService {
    var kallteller = 0
    var sisteKallLagreUtenEnhet = false

    override fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        lagreUtenEnhet: Boolean
    ): SakStatistikkResultat {
        kallteller++
        sisteKallLagreUtenEnhet = lagreUtenEnhet
        return if (lagreUtenEnhet) SakStatistikkResultat.OK else resultat
    }
}
