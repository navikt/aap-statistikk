package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.statistikk.behandling.*
import no.nav.aap.statistikk.enhet.Enhet
import no.nav.aap.statistikk.oppgave.*
import no.nav.aap.statistikk.person.Person
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class SakstatistikkEventSourcingTest {

    private val behandlingRef = UUID.randomUUID()
    private val eventSourcing = SakstatistikkEventSourcing()

    @Test
    fun `kun behandlingsflyt-hendelser gir snapshots uten saksbehandler`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, emptyList())

        assertThat(snapshots).hasSize(1)
        assertThat(snapshots[0].saksbehandler).isNull()
        assertThat(snapshots[0].enhet).isNull()
        assertThat(snapshots[0].avklaringsbehov).isEqualTo("5003")
    }

    @Test
    fun `oppgave reservert for gjeldende avklaringsbehov setter saksbehandler`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(2)
        assertThat(snapshots[0].saksbehandler).isNull()
        assertThat(snapshots[1].saksbehandler).isEqualTo("Kompanjong")
        assertThat(snapshots[1].enhet).isEqualTo("0401")
    }

    @Test
    fun `oppgave reservert for feil avklaringsbehov setter ikke saksbehandler`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = Definisjon.AVKLAR_SYKDOM.kode.name
                ),
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 12, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5006"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[0].saksbehandler).isNull()
        assertThat(snapshots[1].saksbehandler).isEqualTo("Kompanjong")
        assertThat(snapshots[1].avklaringsbehov).isEqualTo("5003")
        assertThat(snapshots[2].saksbehandler).isNull()
        assertThat(snapshots[2].avklaringsbehov).isEqualTo("5006")
    }

    @Test
    fun `hendelser sorteres etter tidspunkt`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                ),
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 12, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5006"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots).isSortedAccordingTo(compareBy { it.tidspunkt })
        assertThat(snapshots[0].tidspunkt).isEqualTo(LocalDateTime.of(2024, 1, 1, 10, 0))
        assertThat(snapshots[1].tidspunkt).isEqualTo(LocalDateTime.of(2024, 1, 1, 11, 0))
        assertThat(snapshots[2].tidspunkt).isEqualTo(LocalDateTime.of(2024, 1, 1, 12, 0))
    }

    @Test
    fun `ny behandlingshendelse nuller ut saksbehandler fra oppgave`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                ),
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 14, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5006"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1].saksbehandler).isEqualTo("Kompanjong")
        assertThat(snapshots[2].saksbehandler).isNull()
        assertThat(snapshots[2].enhet).isNull()
    }

    @Test
    fun `oppgave lukket nuller ut saksbehandler`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    ),
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 12, 0),
                        hendelseType = HendelseType.LUKKET,
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1].saksbehandler).isEqualTo("Kompanjong")
        assertThat(snapshots[2].saksbehandler).isNull()
        assertThat(snapshots[2].enhet).isNull()
    }

    @Test
    fun `ny behandlingshendelse med samme avklaringsbehov bruker sisteSaksbehandler fra behandlingsflyt`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                ),
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 14, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003",
                    sisteSaksbehandlerPåBehandling = "Kvaliguy"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1].saksbehandler).isEqualTo("Kompanjong")
        assertThat(snapshots[2].saksbehandler)
            .describedAs("Should use sisteSaksbehandlerSomLøstebehov when avklaringsbehov matches")
            .isEqualTo("Kvaliguy")
        assertThat(snapshots[2].avklaringsbehov).isEqualTo("5003")
    }

    @Test
    fun `oppgave opprettet oppdaterer enhet`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.OPPRETTET,
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(2)
        assertThat(snapshots[0].enhet).isNull()
        assertThat(snapshots[1].enhet).isEqualTo("0401")
        assertThat(snapshots[1].saksbehandler).isNull()
    }

    private fun lagBehandling(
        hendelser: List<BehandlingHendelse>
    ) = Behandling(
        referanse = behandlingRef,
        sak = Sak(
            id = 0L,
            saksnummer = Saksnummer("123456"),
            person = Person("234"),
            sakStatus = SakStatus.UTREDES,
            sistOppdatert = LocalDateTime.now(),
        ),
        typeBehandling = TypeBehandling.Førstegangsbehandling,
        status = BehandlingStatus.UTREDES,
        opprettetTid = LocalDateTime.now(),
        mottattTid = LocalDateTime.now(),
        versjon = Versjon("1"),
        søknadsformat = SøknadsFormat.PAPIR,
        hendelser = hendelser
    )

    private fun lagBehandlingHendelse(
        tidspunkt: LocalDateTime,
        status: BehandlingStatus,
        avklaringsbehov: String?,
        sisteSaksbehandlerPåBehandling: String? = null
    ) = BehandlingHendelse(
        tidspunkt = tidspunkt,
        hendelsesTidspunkt = tidspunkt,
        status = status,
        saksbehandler = null,
        avklaringsBehov = avklaringsbehov,
        sisteLøsteAvklaringsbehov = null,
        sisteSaksbehandlerSomLøstebehov = sisteSaksbehandlerPåBehandling,
        avklaringsbehovStatus = Status.OPPRETTET,
        versjon = Versjon("1"),
        mottattTid = tidspunkt,
        søknadsformat = SøknadsFormat.PAPIR,
        relatertBehandlingReferanse = null
    )

    private fun lagOppgave(
        avklaringsbehov: String,
        hendelser: List<OppgaveHendelse>
    ) = Oppgave(
        identifikator = 123L,
        avklaringsbehov = avklaringsbehov,
        enhet = Enhet(0L, "0401"),
        person = null,
        status = Oppgavestatus.OPPRETTET,
        opprettetTidspunkt = LocalDateTime.now(),
        behandlingReferanse = BehandlingReferanse(id = null, referanse = behandlingRef),
        hendelser = hendelser
    )

    @Test
    fun `ny behandlingshendelse med nytt avklaringsbehov setter enhet til null`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                ),
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 14, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5006"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.OPPRETTET,
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1].enhet).isEqualTo("0401")
        assertThat(snapshots[1].avklaringsbehov).isEqualTo("5003")
        assertThat(snapshots[2].enhet)
            .describedAs("Should set enhet to null when avklaringsbehov changes")
            .isNull()
        assertThat(snapshots[2].avklaringsbehov).isEqualTo("5006")
    }

    @Test
    fun `ny behandlingshendelse med samme avklaringsbehov beholder enhet`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                ),
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 14, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003",
                    sisteSaksbehandlerPåBehandling = "Kvaliguy"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.OPPRETTET,
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1].enhet).isEqualTo("0401")
        assertThat(snapshots[2].enhet)
            .describedAs("Should keep enhet when avklaringsbehov stays the same")
            .isEqualTo("0401")
        assertThat(snapshots[2].avklaringsbehov).isEqualTo("5003")
    }

    @Test
    fun `nylig opprettet oppgave reservert av samme person beholder enhet og saksbehandler`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.OPPRETTET,
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    ),
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 12, 0),
                        hendelseType = HendelseType.RESERVERT,
                        reservertAv = "Kompanjong",
                        enhet = "0401",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(3)
        assertThat(snapshots[1].enhet).isEqualTo("0401")
        assertThat(snapshots[1].saksbehandler).isNull()
        assertThat(snapshots[2].enhet)
            .describedAs("Skal beholde enhet når samme person reserverer oppgaven")
            .isEqualTo("0401")
        assertThat(snapshots[2].saksbehandler)
            .describedAs("Skal sette saksbehandler når oppgaven reserveres")
            .isEqualTo("Kompanjong")
    }

    @Test
    fun `oppgave opprettet med reservasjon setter både enhet og saksbehandler`() {
        val behandling = lagBehandling(
            hendelser = listOf(
                lagBehandlingHendelse(
                    tidspunkt = LocalDateTime.of(2024, 1, 1, 10, 0),
                    status = BehandlingStatus.OPPRETTET,
                    avklaringsbehov = "5003"
                )
            )
        )

        val oppgaver = listOf(
            lagOppgave(
                avklaringsbehov = "5003",
                hendelser = listOf(
                    lagOppgaveHendelse(
                        tidspunkt = LocalDateTime.of(2024, 1, 1, 11, 0),
                        hendelseType = HendelseType.OPPRETTET,
                        reservertAv = "Brev-Besluttersen",
                        enhet = "4491",
                        avklaringsbehov = "5003"
                    )
                )
            )
        )

        val snapshots = eventSourcing.byggSakstatistikkHendelser(behandling, oppgaver)

        assertThat(snapshots).hasSize(2)
        assertThat(snapshots[0].enhet).isNull()
        assertThat(snapshots[0].saksbehandler).isNull()
        assertThat(snapshots[1].enhet)
            .describedAs("Skal sette enhet når oppgave opprettes med reservasjon")
            .isEqualTo("4491")
        assertThat(snapshots[1].saksbehandler)
            .describedAs("Skal sette saksbehandler når oppgave opprettes med reservasjon")
            .isEqualTo("Brev-Besluttersen")
    }

    private fun lagOppgaveHendelse(
        tidspunkt: LocalDateTime,
        hendelseType: HendelseType,
        reservertAv: String? = null,
        enhet: String,
        avklaringsbehov: String = "5003"
    ) = OppgaveHendelse(
        hendelse = hendelseType,
        oppgaveId = 123L,
        mottattTidspunkt = tidspunkt,
        sendtTid = tidspunkt,
        enhet = enhet,
        avklaringsbehovKode = avklaringsbehov,
        status = Oppgavestatus.OPPRETTET,
        reservertAv = reservertAv,
        reservertTidspunkt = if (reservertAv != null) tidspunkt else null,
        opprettetTidspunkt = tidspunkt,
        endretAv = reservertAv,
        endretTidspunkt = tidspunkt,
        versjon = 1L
    )
}
