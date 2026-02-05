package no.nav.aap.statistikk.oppgave

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class OppgaveKtTest {
    @Test
    fun `bygge oppgave fra hendelser`() {
        val behandlingRef = UUID.randomUUID()
        val hendelser = listOf(
            OppgaveHendelse(
                hendelse = HendelseType.OPPRETTET,
                oppgaveId = 123L,
                mottattTidspunkt = LocalDateTime.now(),
                personIdent = "12345678901",
                saksnummer = "S12345",
                behandlingRef = behandlingRef,
                journalpostId = 123,
                enhet = "NAVKontor123",
                avklaringsbehovKode = "Kode123",
                status = Oppgavestatus.OPPRETTET,
                reservertAv = "Saksbehandler123",
                reservertTidspunkt = LocalDateTime.now(),
                opprettetTidspunkt = LocalDateTime.now(),
                endretAv = "SaksbehandlerEndret123",
                endretTidspunkt = LocalDateTime.now(),
                sendtTid = LocalDateTime.now().minusSeconds(1),
                versjon = 12L,
            )
        )

        val oppgave = hendelser.tilOppgave()

        assertThat(oppgave.enhet.kode).isEqualTo("NAVKontor123")
        assertThat(oppgave.behandlingReferanse!!.referanse).isEqualTo(behandlingRef)
    }
}