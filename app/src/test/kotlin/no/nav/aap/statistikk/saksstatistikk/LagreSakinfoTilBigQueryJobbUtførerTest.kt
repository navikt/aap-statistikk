package no.nav.aap.statistikk.saksstatistikk

import io.mockk.mockk
import no.nav.aap.komponenter.repository.RepositoryProvider
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.testutils.MockJobbAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class LagreSakinfoTilBigQueryJobbUtførerTest {

    private val testConfig = EnhetRetryConfig(maxRetries = 3, delaySeconds = 60)
    private val fakeRepositoryProvider = mockk<RepositoryProvider>()
    private val testHendelsestid = LocalDateTime.of(2024, 1, 1, 10, 0)

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
        assertThat(service.sisteKallLagreUtenEnhet).isFalse()
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(1)
        assertThat(jobbAppender.sisteDelayInSeconds).isEqualTo(testConfig.delaySeconds)
        assertThat(jobbAppender.sisteOriginalHendelsestid).isEqualTo(testHendelsestid)
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
        assertThat(service.sisteKallLagreUtenEnhet).isFalse()
        assertThat(service.sisteKallOriginalHendelsestid).isEqualTo(testHendelsestid)
        assertThat(jobbAppender.sisteEnhetRetryCount).isEqualTo(2)
        assertThat(jobbAppender.sisteOriginalHendelsestid).isEqualTo(testHendelsestid)
    }

    @Test
    fun `lagrer med null enhet etter maks antall forsøk`() {
        val behandlingId = BehandlingId(1)
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, "AVKLAR_SYKDOM", testHendelsestid)
        )
        val jobbAppender = MockJobbAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(behandlingId)
            .medParameter("enhetRetryCount", testConfig.maxRetries.toString())
            .medParameter("originalHendelsestid", testHendelsestid.toString())
        utfører.utfør(input)

        // Første kall returnerer ManglerEnhet, andre kall med lagreUtenEnhet=true
        assertThat(service.kallteller).isEqualTo(2)
        assertThat(service.sisteKallLagreUtenEnhet).isTrue()
        assertThat(service.sisteKallOriginalHendelsestid).isEqualTo(testHendelsestid)
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
}

private class FakeSaksStatistikkService(
    private val resultat: SakStatistikkResultat
) : ISaksStatistikkService {
    var kallteller = 0
    var sisteKallLagreUtenEnhet = false
    var sisteKallOriginalHendelsestid: LocalDateTime? = null

    override fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        lagreUtenEnhet: Boolean
    ): SakStatistikkResultat {
        kallteller++
        sisteKallLagreUtenEnhet = lagreUtenEnhet
        sisteKallOriginalHendelsestid = null
        return if (lagreUtenEnhet) SakStatistikkResultat.OK else resultat
    }

    override fun lagreMedOppgavedata(
        behandlingId: BehandlingId,
        originalHendelsestid: LocalDateTime,
        lagreUtenEnhet: Boolean
    ): SakStatistikkResultat {
        kallteller++
        sisteKallLagreUtenEnhet = lagreUtenEnhet
        sisteKallOriginalHendelsestid = originalHendelsestid
        return if (lagreUtenEnhet) SakStatistikkResultat.OK else resultat
    }
}
