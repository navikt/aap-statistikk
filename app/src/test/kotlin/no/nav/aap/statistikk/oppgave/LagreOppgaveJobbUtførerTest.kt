package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.enhet.EnhetRepository
import no.nav.aap.statistikk.enhet.SaksbehandlerRepository
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class LagreOppgaveJobbUtførerTest {
    @Test
    fun `opprette oppgave-entry basert på historikk`(@Postgres dataSource: DataSource) {
        val behandling = settOppEksisterendeBehandling(dataSource)
        val oppgaveId = 123L

        dataSource.transaction {
            OppgaveHendelseRepository(it).lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    mottattTidspunkt = LocalDateTime.now(),
                    personIdent = "12345678901",
                    saksnummer = "S12345",
                    behandlingRef = behandling.referanse,
                    journalpostId = 123,
                    enhet = "NAVKontor123",
                    avklaringsbehovKode = "Kode123",
                    status = Oppgavestatus.OPPRETTET,
                    reservertAv = "Saksbehandler123",
                    reservertTidspunkt = LocalDateTime.now(),
                    opprettetTidspunkt = LocalDateTime.now(),
                    endretAv = "SaksbehandlerEndret123",
                    endretTidspunkt = LocalDateTime.now(),
                    oppgaveId = oppgaveId
                )
            )
        }

        dataSource.transaction {
            LagreOppgaveJobbUtfører(
                oppgaveHendelseRepository = OppgaveHendelseRepository(it),
                behandlingRepository = BehandlingRepository(it),
                personRepository = PersonRepository(it),
                oppgaveRepository = OppgaveRepository(it),
                enhetRepository = EnhetRepository(it),
                saksbehandlerRepository = SaksbehandlerRepository(it)
            ).utfør(JobbInput(LagreOppgaveJobbUtfører).medPayload(oppgaveId.toString()))
        }

        val oppgaverPåBehandling = dataSource.transaction {
            OppgaveRepository(it).hentOppgaverForBehandling(behandling.id!!)
        }

        assertThat(oppgaverPåBehandling).isNotEmpty
        val førsteOppgave = oppgaverPåBehandling.first()
        assertThat(førsteOppgave.enhet.kode).isEqualTo("NAVKontor123")
        assertThat(førsteOppgave.forBehandling).isEqualTo(behandling.id!!)
        assertThat(førsteOppgave.person?.id).isNotNull

        assertThat(førsteOppgave.reservasjon).isNotNull
    }

    @Test
    fun `oppgave urelatert til person og behandling`(@Postgres dataSource: DataSource) {
        val enhet = "NAVKontor456"
        val oppgaveId = 124L
        dataSource.transaction {
            OppgaveHendelseRepository(it).lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    mottattTidspunkt = LocalDateTime.now(),
                    journalpostId = 123,
                    enhet = enhet,
                    avklaringsbehovKode = "POST_MOTTAK_NOE",
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = LocalDateTime.now().minusSeconds(10),
                    endretAv = "SaksbehandlerEndret4232",
                    endretTidspunkt = LocalDateTime.now(),
                    oppgaveId = oppgaveId
                )
            )
        }

        dataSource.transaction {
            LagreOppgaveJobbUtfører(
                oppgaveHendelseRepository = OppgaveHendelseRepository(it),
                behandlingRepository = BehandlingRepository(it),
                personRepository = PersonRepository(it),
                oppgaveRepository = OppgaveRepository(it),
                enhetRepository = EnhetRepository(it),
                saksbehandlerRepository = SaksbehandlerRepository(it)
            ).utfør(JobbInput(LagreOppgaveJobbUtfører).medPayload(oppgaveId.toString()))
        }

        val oppgaverForEnhet = dataSource.transaction {
            val uthentetEnhet = EnhetRepository(it).hentEnhet(enhet)!!
            OppgaveRepository(it).hentOppgaverForEnhet(uthentetEnhet)
        }

        assertThat(oppgaverForEnhet).isNotEmpty
        assertThat(oppgaverForEnhet.first().enhet.kode).isEqualTo("NAVKontor456")

    }


    private fun settOppEksisterendeBehandling(dataSource: DataSource): Behandling {
        return dataSource.transaction {
            val personUtenId = Person(
                ident = "123"
            )
            val id = PersonRepository(it).lagrePerson(personUtenId)
            val sak = Sak(
                saksnummer = "123",
                person = personUtenId.copy(id = id),
                sakStatus = SakStatus.OPPRETTET,
                sistOppdatert = LocalDateTime.now()
            )
            val sakId = SakRepositoryImpl(it).settInnSak(sak)
            val behandling = Behandling(
                referanse = UUID.randomUUID(),
                sak = sak.copy(id = sakId),
                typeBehandling = TypeBehandling.Klage,
                status = BehandlingStatus.UTREDES,
                opprettetTid = LocalDateTime.now().minusSeconds(10),
                mottattTid = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                versjon = Versjon(
                    verdi = "..."
                ),
                søknadsformat = SøknadsFormat.DIGITAL,
            )
            val behandlingId = BehandlingRepository(it).opprettBehandling(
                behandling
            )
            behandling.copy(id = behandlingId)
        }
    }
}