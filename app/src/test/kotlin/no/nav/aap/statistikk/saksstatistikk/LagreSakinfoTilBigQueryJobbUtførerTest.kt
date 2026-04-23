package no.nav.aap.statistikk.saksstatistikk

import io.mockk.every
import io.mockk.mockk
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.testutils.MockJobbAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
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

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(0)
    }

    @Test
    fun `reschedulerer når enhet mangler ved første forsøk`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", lagFakeBQBehandling())
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId))
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
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", lagFakeBQBehandling())
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        // retryCount=0 → delay = 60 * 2^0 = 60
        utfører.utfør(JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId)))
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(60L)

        // retryCount=1 → delay = 60 * 2^1 = 120
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1))
        )
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(120L)

        // retryCount=2 → delay = 60 * 2^2 = 240
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 2))
        )
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(240L)
    }

    @Test
    fun `reschedulerer med økt retry-teller`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", lagFakeBQBehandling())
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallLagreUtenEnhet).isFalse()
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(2)
    }

    @Test
    fun `kaster exception etter maks antall forsøk når enhet mangler`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", lagFakeBQBehandling())
        )
        val jobbAppender = MockJobbAppender()
        val fakeBehandlingRepository = mockk<BehandlingRepository>(relaxed = true) {
            every { hent(behandlingId) } returns mockk { every { referanse } returns UUID.randomUUID() }
        }
        every { fakeRepositoryProvider.provide<BehandlingRepository>() } returns fakeBehandlingRepository

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = testConfig.maxRetries))

        assertThrows<IllegalStateException> {
            utfører.utfør(input)
        }
    }

    @Test
    fun `config kan overstyres i test`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", lagFakeBQBehandling())
        )
        val jobbAppender = MockJobbAppender()
        val customConfig = EnhetRetryConfig(maxRetries = 1, delaySeconds = 10)

        val utfører = lagUtfører(service, jobbAppender, config = customConfig)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId))
        utfører.utfør(input)

        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(10)
    }

    @Test
    fun `ManglerEnhet inneholder avklaringsbehovkode`() {
        val behandlingId = BehandlingId(1)
        val avklaringsbehovKode = "AVKLAR_SYKDOM"
        val resultat = SakStatistikkResultat.ManglerEnhet(behandlingId, avklaringsbehovKode, lagFakeBQBehandling())

        assertThat(resultat.avklaringsbehovKode).isEqualTo(avklaringsbehovKode)
        assertThat(resultat.behandlingId).isEqualTo(behandlingId)
    }

    @Test
    fun `ved retry uten stored BQBehandling brukes lagreSakInfoTilBigquery`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallVarMedStoredBQBehandling).isFalse()
    }

    @Test
    fun `ved retry med stored BQBehandling brukes lagreMedStoredBQBehandling`() {
        val behandlingId = BehandlingId(1)
        val fakeBQBehandling = lagFakeBQBehandling()
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1, storedBQBehandling = fakeBQBehandling, avklaringsbehovKode = "AVKLAR_SYKDOM"))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallVarMedStoredBQBehandling).isTrue()
        assertThat(service.sisteStoredBQBehandling?.behandlingUUID).isEqualTo(fakeBQBehandling.behandlingUUID)
    }

    @Test
    fun `stored BQBehandling sendes med til neste retry`() {
        val behandlingId = BehandlingId(1)
        val fakeBQBehandling = lagFakeBQBehandling()
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", fakeBQBehandling)
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)
        utfører.utfør(JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId)))

        assertThat(jobbAppender.sisteStoredBQBehandling).isNotNull()
        assertThat(jobbAppender.sisteStoredBQBehandling!!.behandlingUUID).isEqualTo(fakeBQBehandling.behandlingUUID)
        assertThat(jobbAppender.sisteAvklaringsbehovKode).isEqualTo("AVKLAR_SYKDOM")
    }
}

private fun lagFakeBQBehandling(behandlingUUID: UUID = UUID.randomUUID()) = BQBehandling(
    behandlingUUID = behandlingUUID,
    behandlingType = "FØRSTEGANGSBEHANDLING",
    aktorId = "12345678901",
    saksnummer = "123",
    tekniskTid = LocalDateTime.now(),
    registrertTid = LocalDateTime.now().minusDays(1),
    endretTid = LocalDateTime.now(),
    versjon = "v1",
    mottattTid = LocalDateTime.now().minusDays(1),
    opprettetAv = "Kelvin",
    ansvarligBeslutter = null,
    søknadsFormat = SøknadsFormat.DIGITAL,
    saksbehandler = null,
    behandlingMetode = BehandlingMetode.MANUELL,
    behandlingStatus = "UNDER_BEHANDLING",
    behandlingÅrsak = "SØKNAD",
    resultatBegrunnelse = null,
    ansvarligEnhetKode = null,
    sakYtelse = "AAP",
    erResending = false,
)

private class FakeSaksStatistikkService(
    private val resultat: SakStatistikkResultat
) : ISaksStatistikkService {
    var kallteller = 0
    var sisteKallLagreUtenEnhet = false
    var sisteKallVarMedStoredBQBehandling = false
    var sisteStoredBQBehandling: BQBehandling? = null

    override fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        lagreUtenEnhet: Boolean
    ): SakStatistikkResultat {
        kallteller++
        sisteKallLagreUtenEnhet = lagreUtenEnhet
        sisteKallVarMedStoredBQBehandling = false
        return if (lagreUtenEnhet) SakStatistikkResultat.OK else resultat
    }

    override fun lagreMedStoredBQBehandling(
        behandlingId: BehandlingId,
        storedBQBehandling: BQBehandling,
        avklaringsbehovKode: String?,
    ): SakStatistikkResultat {
        kallteller++
        sisteKallVarMedStoredBQBehandling = true
        sisteStoredBQBehandling = storedBQBehandling
        return resultat
    }
}
