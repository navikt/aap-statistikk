package no.nav.aap.statistikk

import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.ContentType
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
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

        testKlient(
            transactionExecutor = noOpTransactionExecutor,
            motor = motorMock(),
            jobbAppender = jobbAppender,
            azureConfig = azureConfig,
            lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(jobbAppender)
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
  "vurderingsbehov":  [],
  "årsakTilOpprettelse": "SØKNAD",
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
  "vurderingsbehov":  [],
  "årsakTilBehandling":  [],
  "årsakTilOpprettelse": "SØKNAD",
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

        testKlient(
            noOpTransactionExecutor,
            motorMock(),
            azureConfig,
            LagreStoppetHendelseJobb(jobbAppender),
            jobbAppender,
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