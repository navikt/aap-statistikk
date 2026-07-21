package no.nav.aap.statistikk

import io.mockk.mockk
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.statistikk.jobber.LagreStoppetHendelseJobb
import no.nav.aap.statistikk.testutils.*
import no.nav.aap.statistikk.testutils.client.testKlient
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

@Fakes
class ApplicationTest {

    @Test
    fun `kan poste mottastatistikk, og jobb blir opprettet`() {
        val jobbAppender = MockJobbAppender()

        testKlient(
            transactionExecutor = noOpTransactionExecutor,
            motor = motorMock(),
            jobbAppender = jobbAppender,
            lagreStoppetHendelseJobb = LagreStoppetHendelseJobb(jobbAppender, mockk())
        ) {
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
            postBehandlingsflytHendelse(DefaultJsonMapper.fromJson(body))
        }

        assertThat(jobbAppender.jobber).hasSize(1)
    }

    @Test
    fun `godtar payload med ukjente felter`() {
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
            LagreStoppetHendelseJobb(jobbAppender, mockk()),
            jobbAppender,
        ) {
            postBehandlingsflytHendelse(DefaultJsonMapper.fromJson(payload))
        }
    }
}