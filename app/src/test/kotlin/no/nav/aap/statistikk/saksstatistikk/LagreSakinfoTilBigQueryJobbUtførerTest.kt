package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.Behandling
import no.nav.aap.statistikk.behandling.BehandlingId
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.behandling.Versjon
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class LagreSakinfoTilBigQueryJobbUtførerTest {

    private val testConfig = EnhetRetryConfig(maxRetries = 3, delaySeconds = 60)
    private val behandlingRepository = FakeBehandlingRepository()
    private val testSaksnummer = Saksnummer("123456")
    private val behandlingId = behandlingRepository.opprettBehandling(
        Behandling(
            referanse = UUID.randomUUID(),
            sak = Sak(
                id = 1L,
                saksnummer = testSaksnummer,
                person = Person(Ident("12345678901"), id = 1L),
                sakStatus = SakStatus.UTREDES,
                sistOppdatert = LocalDateTime.of(2024, 1, 1, 10, 0),
            ),
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.UTREDES,
            opprettetTid = LocalDateTime.of(2024, 1, 1, 10, 0),
            mottattTid = LocalDateTime.of(2024, 1, 1, 9, 0),
            versjon = Versjon("1.0"),
            søknadsformat = SøknadsFormat.DIGITAL,
        )
    )

    private fun lagUtfører(
        service: ISaksStatistikkService,
        jobbAppender: FakeSakinfoRetryAppender,
        config: EnhetRetryConfig = testConfig
    ) = LagreSakinfoTilBigQueryJobbUtfører(service, behandlingRepository, jobbAppender::leggTil, config)

    @Test
    fun `lagrer normalt når enhet finnes`() {
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = FakeSakinfoRetryAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input =
            JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(jobbAppender.jobber).isEmpty()
    }

    @Test
    fun `reschedulerer når enhet mangler ved første forsøk`() {
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(
                behandlingId,
                Definisjon.AVKLAR_SYKDOM,
            )
        )
        val jobbAppender = FakeSakinfoRetryAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val payload = LagreSakinfoPayload(
            behandlingId,
            triggerKilde = "oppgave",
            cutoffTidspunkt = LocalDateTime.of(2024, 1, 1, 12, 0),
        )
        val input = JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(payload)
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(jobbAppender.jobber).containsExactly(
            payload.copy(
                avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name,
                retryCount = 1,
                triggerKilde = "retry(oppgave)",
            ) to testConfig.delaySeconds
        )
    }

    @Test
    fun `delay er eksponentielt basert på retry-teller`() {
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(
                behandlingId,
                Definisjon.AVKLAR_SYKDOM,
            )
        )
        val jobbAppender = FakeSakinfoRetryAppender()

        val utfører = lagUtfører(service, jobbAppender)

        // retryCount=0 → delay = 60 * 2^0 = 60
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(
                LagreSakinfoPayload(
                    behandlingId
                )
            )
        )
        assertThat(jobbAppender.jobber.last().second).isEqualTo(60L)

        // retryCount=1 → delay = 60 * 2^1 = 120
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1))
        )
        assertThat(jobbAppender.jobber.last().second).isEqualTo(120L)

        // retryCount=2 → delay = 60 * 2^2 = 240
        utfører.utfør(
            JobbInput(LagreSakinfoTilBigQueryJobb())
                .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 2))
        )
        assertThat(jobbAppender.jobber.last().second).isEqualTo(240L)
    }

    @Test
    fun `reschedulerer med økt retry-teller`() {
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, Definisjon.AVKLAR_SYKDOM)
        )
        val jobbAppender = FakeSakinfoRetryAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
        assertThat(jobbAppender.jobber.single().first.retryCount).isEqualTo(2)
    }

    @Test
    fun `kaster exception etter maks antall forsøk når enhet mangler`() {
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, Definisjon.AVKLAR_SYKDOM)
        )
        val jobbAppender = FakeSakinfoRetryAppender()

        val utfører = lagUtfører(service, jobbAppender)

        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = testConfig.maxRetries))

        val exception = assertThrows<IllegalStateException> {
            utfører.utfør(input)
        }
        assertThat(exception).hasMessage(
            "Enhet mangler fortsatt etter ${testConfig.maxRetries} forsøk " +
                    "for behandling $behandlingId, " +
                    "avklaringsbehov=${Definisjon.AVKLAR_SYKDOM}. Saksnummer: $testSaksnummer."
        )
        assertThat(jobbAppender.jobber).isEmpty()
    }

    @Test
    fun `config kan overstyres i test`() {
        val service = FakeSaksStatistikkService(
            SakStatistikkResultat.ManglerEnhet(behandlingId, Definisjon.AVKLAR_SYKDOM)
        )
        val jobbAppender = FakeSakinfoRetryAppender()
        val customConfig = EnhetRetryConfig(maxRetries = 1, delaySeconds = 10)

        val utfører = lagUtfører(service, jobbAppender, config = customConfig)

        val input =
            JobbInput(LagreSakinfoTilBigQueryJobb()).medPayload(LagreSakinfoPayload(behandlingId))
        utfører.utfør(input)

        assertThat(jobbAppender.jobber.single().second).isEqualTo(10)
    }

    @Test
    fun `ManglerEnhet inneholder avklaringsbehovkode`() {
        val avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM
        val resultat = SakStatistikkResultat.ManglerEnhet(
            behandlingId,
            avklaringsbehovKode,
        )

        assertThat(resultat.avklaringsbehovKode).isEqualTo(avklaringsbehovKode)
        assertThat(resultat.behandlingId).isEqualTo(behandlingId)
    }

    @Test
    fun `ved retry brukes alltid lagreSakInfoTilBigquery — ikke frossen snapshot`() {
        val service = FakeSaksStatistikkService(SakStatistikkResultat.OK)
        val jobbAppender = FakeSakinfoRetryAppender()

        val utfører = lagUtfører(service, jobbAppender)

        // Retry med retryCount=1 skal fortsatt kalle lagreSakInfoTilBigquery
        val input = JobbInput(LagreSakinfoTilBigQueryJobb())
            .medPayload(LagreSakinfoPayload(behandlingId, retryCount = 1))
        utfører.utfør(input)

        assertThat(service.kallteller).isEqualTo(1)
    }

}

private class FakeSakinfoRetryAppender {
    var jobber = emptyList<Pair<LagreSakinfoPayload, Long>>()
        private set

    fun leggTil(payload: LagreSakinfoPayload, delayInSeconds: Long) {
        jobber = jobber + (payload to delayInSeconds)
    }
}

private class FakeSaksStatistikkService(
    private val resultat: SakStatistikkResultat
) : ISaksStatistikkService {
    var kallteller = 0

    override fun lagreSakInfoTilBigquery(
        behandlingId: BehandlingId,
        cutoffTidspunkt: LocalDateTime?,
    ): SakStatistikkResultat {
        kallteller++
        return resultat
    }
}
