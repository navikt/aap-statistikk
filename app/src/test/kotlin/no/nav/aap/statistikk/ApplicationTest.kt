package no.nav.aap.statistikk

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.ContentType
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.statistikk.behandling.DiagnoseRepositoryImpl
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.oppgave.LagreOppgaveHendelseJobb
import no.nav.aap.statistikk.pdl.SkjermingService
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.postmottak.LagrePostmottakHendelseJobb
import no.nav.aap.statistikk.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.net.URI

@Fakes
class ApplicationTest {

    @Test
    fun `kan poste mottastatistikk, og jobb blir opprettet`(
        @Fakes azureConfig: AzureConfig
    ) {
        val jobbAppender = MockJobbAppender()
        val bqRepository = FakeBQSakRepository()
        val bqRepositoryYtelse = FakeBQYtelseRepository()
        val meterRegistry = SimpleMeterRegistry()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            azureConfig,
            LagreStoppetHendelseJobb(
                bqRepositoryYtelse,
                bqRepository,
                meterRegistry,
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() },
                tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
                beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
                vilkårsResultatRepositoryFactory = { FakeVilkårsResultatRepository() },
                behandlingRepositoryFactory = { FakeBehandlingRepository() },
                diagnoseRepository = { DiagnoseRepositoryImpl(it) },
                personService = { PersonService(FakePersonRepository()) },
                rettighetstypeperiodeRepository = { FakeRettighetsTypeRepository() },
                skjermingService = SkjermingService(FakePdlClient())
            ), LagreOppgaveHendelseJobb(meterRegistry),
            LagrePostmottakHendelseJobb(meterRegistry)
        ) { url, client ->
            @Language("JSON") val body = """{
  "saksnummer": "123456789",
  "sakStatus": "OPPRETTET",
  "behandlingReferanse": "f14dfc5a-9536-4050-a10b-ebe554ecfdd2",
  "behandlingOpprettetTidspunkt": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "status": "OPPRETTET",
  "behandlingStatus": "OPPRETTET",
  "behandlingType": "Førstegangsbehandling",
  "ident": "1403199012345",
  "avklaringsbehov": [],
  "årsakTilBehandling":  [],
  "versjon": "UKJENT",
  "mottattTid": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "hendelsesTidspunkt": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ]
}"""

            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    DefaultJsonMapper.fromJson(body), contentType = ContentType.APPLICATION_JSON
                )
            )

        }

        assertThat(jobbAppender.jobber).hasSize(1)
    }

    @Test
    fun `godtar payload med ukjente felter`(
        @Fakes azureConfig: AzureConfig
    ) {
        @Language("JSON") val payload = """{
  "saksnummer": "123456789",
  "sakStatus": "OPPRETTET",
  "behandlingReferanse": "f14dfc5a-9536-4050-a10b-ebe554ecfdd2",
  "behandlingOpprettetTidspunkt": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "status": "OPPRETTET",
  "behandlingStatus": "OPPRETTET",
  "behandlingType": "Førstegangsbehandling",
  "ident": "1403199012345",
  "avklaringsbehov": [],
  "årsakTilBehandling":  [],
  "versjon": "UKJENT",
  "mottattTid": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "hendelsesTidspunkt": [
    2024,
    8,
    14,
    11,
    5,
    10,
    343319000
  ],
  "ukjentfelt": "hei"
}"""

        val jobbAppender = MockJobbAppender()
        val bqRepository = FakeBQSakRepository()
        val bqRepositoryYtelse = FakeBQYtelseRepository()
        val meterRegistry = SimpleMeterRegistry()

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            jobbAppender,
            azureConfig,
            LagreStoppetHendelseJobb(
                bqRepositoryYtelse, bqRepository, meterRegistry,
                bigQueryKvitteringRepository = { FakeBigQueryKvitteringRepository() },
                tilkjentYtelseRepositoryFactory = { FakeTilkjentYtelseRepository() },
                beregningsgrunnlagRepositoryFactory = { FakeBeregningsgrunnlagRepository() },
                vilkårsResultatRepositoryFactory = { FakeVilkårsResultatRepository() },
                behandlingRepositoryFactory = { FakeBehandlingRepository() },
                diagnoseRepository = { DiagnoseRepositoryImpl(it) },
                personService = { PersonService(FakePersonRepository()) },
                rettighetstypeperiodeRepository = { FakeRettighetsTypeRepository() },
                skjermingService = SkjermingService(FakePdlClient()),
            ), LagreOppgaveHendelseJobb(meterRegistry), LagrePostmottakHendelseJobb(meterRegistry)
        ) { url, client ->
            client.post<StoppetBehandling, Any>(
                URI.create("$url/stoppetBehandling"), PostRequest(
                    DefaultJsonMapper.fromJson<StoppetBehandling>(payload),
                    additionalHeaders = listOf(
                        Header("Accept", "application/json"),
                        Header("Content-Type", "application/json")
                    )
                )
            )
        }
    }
}