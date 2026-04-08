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
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class LagreSakinfoTilBigQueryJobbUtførerTest {

    private val testConfig = EnhetRetryConfig(maxRetries = 3, delaySeconds = 60)
    private val fakeRepositoryProvider = mockk<RepositoryProvider>()
    private val testHendelsestid = LocalDateTime.of(2024, 1, 1, 10, 0)

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
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", testHendelsestid)
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(behandlingId)
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(1)
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(testConfig.delaySeconds)
        assertThat(jobbAppender.sisteOriginalHendelsestid).isEqualTo(testHendelsestid)
    }

    @Test
    fun `delay er eksponentielt basert på retry-teller`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", testHendelsestid)
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
                .medParameter("originalHendelsestid", testHendelsestid.toString())
        )
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(120L)

        // retryCount=2 → delay = 60 * 2^2 = 240
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(behandlingId)
                .medParameter("enhetRetryCount", "2")
                .medParameter("originalHendelsestid", testHendelsestid.toString())
        )
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(240L)
    }

    @Test
    fun `reschedulerer med økt retry-teller og bevarer originalHendelsestid`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", testHendelsestid)
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("enhetRetryCount", "1")
            .medParameter("originalHendelsestid", testHendelsestid.toString())
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallOriginalHendelsestid).isEqualTo(testHendelsestid)
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(2)
        assertThat(jobbAppender.sisteOriginalHendelsestid).isEqualTo(testHendelsestid)
    }

    @Test
    fun `kaster exception etter maks antall forsøk`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", testHendelsestid)
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
            .medParameter("originalHendelsestid", testHendelsestid.toString())

        // Etter maks antall forsøk kastes exception — lagres IKKE med null enhet
        assertThrows<IllegalStateException> {
            utfører.utfør(input)
        }
        assertThat(service.kallteller).isEqualTo(1)
    }

    @Test
    fun `config kan overstyres i test`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", testHendelsestid)
        )
        val jobbAppender = MockJobbAppender()
        val customConfig = EnhetRetryConfig(maxRetries = 1, delaySeconds = 10)

        val utfører = lagUtfører(service, jobbAppender, config = customConfig)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(behandlingId)
        utfører.utfør(input)

        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(10)
    }

    @Test
    fun `ManglerEnhet inneholder avklaringsbehovkode og hendelsestid`() {
        val behandlingId = BehandlingId(1)
        val avklaringsbehovKode = "AVKLAR_SYKDOM"
        val resultat = SakStatistikkResultat.ManglerEnhet(
            behandlingId, avklaringsbehovKode, testHendelsestid
        )

        assertThat(resultat.avklaringsbehovKode).isEqualTo(avklaringsbehovKode)
        assertThat(resultat.behandlingId).isEqualTo(behandlingId)
        assertThat(resultat.hendelsestid).isEqualTo(testHendelsestid)
    }

    @Test
    fun `ved retry brukes lagreMedOppgavedata med originalHendelsestid`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("enhetRetryCount", "1")
            .medParameter("originalHendelsestid", testHendelsestid.toString())
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallOriginalHendelsestid).isEqualTo(testHendelsestid)
    }

    @Test
    fun `ved oppgave-trigget jobb brukes lagreSakInfoMedOppgaveTidspunkt`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = MockJobbAppender()
        val oppgaveSendtTid = LocalDateTime.of(2024, 1, 1, 9, 55)

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("oppgaveSendtTid", oppgaveSendtTid.toString())
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(service.sisteKallOppgaveSendtTid).isEqualTo(oppgaveSendtTid)
        assertThat(service.sisteKallOriginalHendelsestid).isNull()
    }
}

private class FakeSaksStatistikkService(
    private val resultat: SakStatistikkResultat
) : ISaksStatistikkService {
    var kallteller = 0
    var sisteKallOriginalHendelsestid: LocalDateTime? = null
    var sisteKallOppgaveSendtTid: LocalDateTime? = null

    override fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
    ): SakStatistikkResultat {
        kallteller++
        sisteKallOriginalHendelsestid = null
        return resultat
    }

    override fun lagreMedOppgavedata(
        behandlingId: BehandlingId,
        originalHendelsestid: LocalDateTime,
    ): SakStatistikkResultat {
        kallteller++
        sisteKallOriginalHendelsestid = originalHendelsestid
        return resultat
    }

    override fun lagreSakInfoMedOppgaveTidspunkt(
        behandlingId: BehandlingId,
        oppgaveSendtTid: LocalDateTime,
    ): SakStatistikkResultat {
        kallteller++
        sisteKallOppgaveSendtTid = oppgaveSendtTid
        return resultat
    }
}
