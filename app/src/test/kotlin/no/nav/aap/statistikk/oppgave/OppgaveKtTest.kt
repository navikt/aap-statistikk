package no.nav.aap.statistikk.oppgave

import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class OppgaveKtTest {
    @Test
    fun `bygge oppgave fra hendelser`() {
        val hendelser = listOf(
            OppgaveHendelse(
                hendelse = HendelseType.OPPRETTET,
                mottattTidspunkt = LocalDateTime.now(),
                personIdent = "12345678901",
                saksnummer = "S12345",
                behandlingRef = UUID.randomUUID(),
                journalpostId = 123,
                enhet = "NAVKontor123",
                avklaringsbehovKode = "Kode123",
                status = Oppgavestatus.OPPRETTET,
                reservertAv = "Saksbehandler123",
                reservertTidspunkt = LocalDateTime.now(),
                opprettetTidspunkt = LocalDateTime.now(),
                endretAv = "SaksbehandlerEndret123",
                endretTidspunkt = LocalDateTime.now(),
                oppgaveId = 123L
            )
        )

        val oppgave = hendelser.tilOppgave(object : BehandlingResolver {
            override fun resolve(behandlingReferanse: UUID): Behandling {
                return Behandling(
                    referanse = behandlingReferanse,
                    sak = Sak(
                        saksnummer = "2334",
                        person = Person(
                            ident = "1243"
                        ),
                        sakStatus = SakStatus.LØPENDE,
                        sistOppdatert = LocalDateTime.now()
                    ),
                    typeBehandling = TypeBehandling.Klage,
                    status = BehandlingStatus.UTREDES,
                    opprettetTid = LocalDateTime.now(),
                    mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                    versjon = Versjon(
                        verdi = "..."
                    ),
                    søknadsformat = SøknadsFormat.DIGITAL
                )
            }
        })

        assertThat(oppgave.enhet!!.kode).isEqualTo("NAVKontor123")
    }
}