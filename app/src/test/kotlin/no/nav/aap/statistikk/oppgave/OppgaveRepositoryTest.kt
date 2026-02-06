package no.nav.aap.statistikk.oppgave

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.enhet.EnhetRepositoryImpl
import no.nav.aap.statistikk.enhet.SaksbehandlerRepositoryImpl
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

class OppgaveRepositoryTest {
    @Test
    fun `sette inn og hent ut oppgave`(@Postgres dataSource: DataSource) {
        val enhet = dataSource.transaction {
            val enhet = Enhet(kode = "1234")
            enhet.copy(id = EnhetRepositoryImpl(it).lagreEnhet(enhet))
        }

        val oppgave = Oppgave(
            identifikator = 12L,
            avklaringsbehov = "5099",
            enhet = enhet,
            person = null,
            status = Oppgavestatus.AVSLUTTET,
            opprettetTidspunkt = LocalDateTime.now(),
            reservasjon = null,
            behandlingReferanse = BehandlingReferanse(referanse = UUID.randomUUID()),
            hendelser = listOf()
        )

        dataSource.transaction {
            OppgaveRepositoryImpl(it).lagreOppgave(oppgave)
        }

        val res = dataSource.transaction { OppgaveRepositoryImpl(it).hentOppgave(12L) }

        assertThat(res).isNotNull

        assertThat(res!!.identifikator).isEqualTo(oppgave.identifikator)
        assertThat(res.avklaringsbehov).isEqualTo(oppgave.avklaringsbehov)
        assertThat(res.enhet).isEqualTo(oppgave.enhet)
        assertThat(res.person).isNull()
        assertThat(res.status).isEqualTo(oppgave.status)
        assertThat(res.opprettetTidspunkt).isCloseTo(
            oppgave.opprettetTidspunkt,
            within(1, ChronoUnit.SECONDS)
        )
        assertThat(res.reservasjon).isNull()
        assertThat(res.behandlingReferanse!!.referanse).isEqualTo(oppgave.behandlingReferanse!!.referanse)
        assertThat(res.hendelser).isEqualTo(oppgave.hendelser)
    }

    @Test
    fun `hentOppgave henter historikk`(@Postgres dataSource: DataSource) {
        val enhet = dataSource.transaction {
            val enhet = Enhet(kode = "4567")
            enhet.copy(id = EnhetRepositoryImpl(it).lagreEnhet(enhet))
        }

        val saksbehandler = dataSource.transaction {
            val saksbehandler = Saksbehandler(ident = "Z123456")
            saksbehandler.copy(id = SaksbehandlerRepositoryImpl(it).lagreSaksbehandler(saksbehandler))
        }

        val behandlingRef = UUID.randomUUID()
        val oppgaveId = 123L

        dataSource.transaction {
            val hendelseRepo = OppgaveHendelseRepositoryImpl(it)

            hendelseRepo.lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveId = oppgaveId,
                    mottattTidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    sendtTid = LocalDateTime.of(2024, 1, 1, 10, 0),
                    enhet = enhet.kode,
                    avklaringsbehovKode = "5099",
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    behandlingRef = behandlingRef,
                    versjon = 1
                )
            )

            hendelseRepo.lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.RESERVERT,
                    oppgaveId = oppgaveId,
                    mottattTidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                    sendtTid = LocalDateTime.of(2024, 1, 1, 11, 0),
                    enhet = enhet.kode,
                    avklaringsbehovKode = "5099",
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    behandlingRef = behandlingRef,
                    reservertAv = saksbehandler.ident,
                    reservertTidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                    versjon = 2
                )
            )
        }

        val oppgave = Oppgave(
            identifikator = oppgaveId,
            avklaringsbehov = "5099",
            enhet = enhet,
            person = null,
            status = Oppgavestatus.OPPRETTET,
            opprettetTidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
            reservasjon = Reservasjon(
                reservertAv = saksbehandler,
                reservasjonOpprettet = LocalDateTime.of(2024, 1, 1, 11, 0)
            ),
            behandlingReferanse = BehandlingReferanse(referanse = behandlingRef),
            hendelser = listOf()
        )

        dataSource.transaction {
            OppgaveRepositoryImpl(it).lagreOppgave(oppgave)
        }

        val res = dataSource.transaction { OppgaveRepositoryImpl(it).hentOppgave(oppgaveId) }

        assertThat(res).isNotNull
        assertThat(res!!.hendelser).hasSize(2)
        assertThat(res.hendelser[0].hendelse).isEqualTo(HendelseType.OPPRETTET)
        assertThat(res.hendelser[1].hendelse).isEqualTo(HendelseType.RESERVERT)
        assertThat(res.hendelser[1].reservertAv).isEqualTo(saksbehandler.ident)
    }

    @Test
    fun `oppgave bygget fra historikk er lik oppgave fra repository`(@Postgres dataSource: DataSource) {
        val enhet = dataSource.transaction {
            val enhet = Enhet(kode = "7890")
            enhet.copy(id = EnhetRepositoryImpl(it).lagreEnhet(enhet))
        }

        val saksbehandler = dataSource.transaction {
            val saksbehandler = Saksbehandler(ident = "X654321")
            saksbehandler.copy(id = SaksbehandlerRepositoryImpl(it).lagreSaksbehandler(saksbehandler))
        }

        val behandlingRef = UUID.randomUUID()
        val oppgaveId = 456L

        dataSource.transaction {
            val hendelseRepo = OppgaveHendelseRepositoryImpl(it)

            hendelseRepo.lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.OPPRETTET,
                    oppgaveId = oppgaveId,
                    mottattTidspunkt = LocalDateTime.of(2024, 2, 1, 10, 0),
                    sendtTid = LocalDateTime.of(2024, 2, 1, 10, 0),
                    enhet = enhet.kode,
                    avklaringsbehovKode = "5001",
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = LocalDateTime.of(2024, 2, 1, 10, 0),
                    behandlingRef = behandlingRef,
                    versjon = 1
                )
            )

            hendelseRepo.lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.RESERVERT,
                    oppgaveId = oppgaveId,
                    mottattTidspunkt = LocalDateTime.of(2024, 2, 1, 12, 0),
                    sendtTid = LocalDateTime.of(2024, 2, 1, 12, 0),
                    enhet = enhet.kode,
                    avklaringsbehovKode = "5001",
                    status = Oppgavestatus.OPPRETTET,
                    opprettetTidspunkt = LocalDateTime.of(2024, 2, 1, 10, 0),
                    behandlingRef = behandlingRef,
                    reservertAv = saksbehandler.ident,
                    reservertTidspunkt = LocalDateTime.of(2024, 2, 1, 12, 0),
                    versjon = 2
                )
            )

            hendelseRepo.lagreHendelse(
                OppgaveHendelse(
                    hendelse = HendelseType.LUKKET,
                    oppgaveId = oppgaveId,
                    mottattTidspunkt = LocalDateTime.of(2024, 2, 1, 14, 0),
                    sendtTid = LocalDateTime.of(2024, 2, 1, 14, 0),
                    enhet = enhet.kode,
                    avklaringsbehovKode = "5001",
                    status = Oppgavestatus.AVSLUTTET,
                    opprettetTidspunkt = LocalDateTime.of(2024, 2, 1, 10, 0),
                    behandlingRef = behandlingRef,
                    reservertAv = saksbehandler.ident,
                    reservertTidspunkt = LocalDateTime.of(2024, 2, 1, 12, 0),
                    versjon = 3
                )
            )
        }

        val oppgave = Oppgave(
            identifikator = oppgaveId,
            avklaringsbehov = "5001",
            enhet = enhet,
            person = null,
            status = Oppgavestatus.AVSLUTTET,
            opprettetTidspunkt = LocalDateTime.of(2024, 2, 1, 10, 0),
            reservasjon = Reservasjon(
                reservertAv = saksbehandler,
                reservasjonOpprettet = LocalDateTime.of(2024, 2, 1, 12, 0)
            ),
            behandlingReferanse = BehandlingReferanse(referanse = behandlingRef),
            hendelser = listOf()
        )

        dataSource.transaction {
            OppgaveRepositoryImpl(it).lagreOppgave(oppgave)
        }

        val fromRepo = dataSource.transaction {
            OppgaveRepositoryImpl(it).hentOppgave(oppgaveId)
        }

        assertThat(fromRepo).isNotNull
        assertThat(fromRepo!!.hendelser).hasSize(3)

        val fromHistory = fromRepo.hendelser.tilOppgave()

        assertThat(fromHistory.identifikator).isEqualTo(fromRepo.identifikator)
        assertThat(fromHistory.avklaringsbehov).isEqualTo(fromRepo.avklaringsbehov)
        assertThat(fromHistory.enhet.kode).isEqualTo(fromRepo.enhet.kode)
        assertThat(fromHistory.status).isEqualTo(fromRepo.status)
        assertThat(fromHistory.reservasjon?.reservertAv?.ident).isEqualTo(fromRepo.reservasjon?.reservertAv?.ident)
        assertThat(fromHistory.behandlingReferanse?.referanse).isEqualTo(fromRepo.behandlingReferanse?.referanse)
        assertThat(fromHistory.sisteHendelse).isEqualTo(HendelseType.LUKKET)
    }
}