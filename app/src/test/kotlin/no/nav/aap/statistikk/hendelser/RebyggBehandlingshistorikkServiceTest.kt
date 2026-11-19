package no.nav.aap.statistikk.hendelser

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.GradBehov
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.tilAvklaringsbehovKode
import no.nav.aap.statistikk.jobber.appender.StatistikkHendelse
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.skjerming.SkjermingService
import no.nav.aap.statistikk.testutils.FakeHendelsePublisher
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestHendelse
import no.nav.aap.statistikk.testutils.builders.opprettTestStoppetBehandling
import no.nav.aap.statistikk.testutils.fakes.FakeBehandlingRepository
import no.nav.aap.statistikk.testutils.fakes.FakePdlGateway
import no.nav.aap.statistikk.testutils.fakes.FakePersonRepository
import no.nav.aap.statistikk.testutils.fakes.FakeSakRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class RebyggBehandlingshistorikkServiceTest {
    @Test
    fun `rekjøring erstatter feil historikk og gir samme aktive historikk ved gjentakelse`(
        @Postgres dataSource: DataSource,
    ) {
        val tidspunkt = LocalDateTime.of(2026, 11, 13, 10, 25)
        val referanse = UUID.randomUUID()
        val saksnummer = Saksnummer("TEST_REPLAY")
        val (behandlingId) = opprettTestHendelse(
            dataSource,
            referanse,
            saksnummer,
            opprettetTidspunkt = tidspunkt,
        )
        dataSource.transaction { connection ->
            connection.execute(
                "UPDATE behandling_historikk SET gjeldende_avklaringsbehov = '5034' WHERE behandling_id = ?",
            ) {
                setParams { setLong(1, behandlingId.id) }
            }
        }
        val hendelse = opprettTestStoppetBehandling(
            referanse,
            tidspunkt,
            tidspunkt.plusMinutes(2),
            tidspunkt,
            saksnummer = saksnummer.value,
            behandlingStatus = no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.UTREDES,
            ident = "29021946",
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SAMORDNING_SYKESTIPEND,
                    status = Status.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = Status.OPPRETTET,
                            tidsstempel = tidspunkt.plusMinutes(1),
                            endretAv = "Kelvin",
                            gradBehov = GradBehov.FRIVILLIG,
                        ),
                    ),
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FATTE_VEDTAK,
                    status = Status.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = Status.OPPRETTET,
                            tidsstempel = tidspunkt.plusMinutes(2),
                            endretAv = "Kelvin",
                            gradBehov = GradBehov.PÅKREVD,
                        ),
                    ),
                ),
            ),
        )
        val publisher = FakeHendelsePublisher()

        fun spillAv() = dataSource.transaction { connection ->
            val repository = BehandlingRepository(connection)
            val pdl = FakePdlGateway()
            RebyggBehandlingshistorikkService(
                SakService(SakRepositoryImpl(connection)),
                PersonService(PersonRepository(connection), pdl),
                repository,
                BehandlingService(repository, SkjermingService(pdl)),
                publisher,
            ).rebyggHistorikk(hendelse)
            repository.hent(behandlingId)
        }

        val gjenoppbygd = spillAv()
        assertThat(gjenoppbygd.id()).isEqualTo(behandlingId)
        assertThat(
            gjenoppbygd.nåværendeTilstand().gjeldendeAvklaringsBehov,
        ).isEqualTo(Definisjon.FATTE_VEDTAK.tilAvklaringsbehovKode())
        assertThat(gjenoppbygd.hendelser)
            .noneMatch {
                it.tilstand.gjeldendeAvklaringsBehov ==
                    Definisjon.AVKLAR_SAMORDNING_SYKESTIPEND.tilAvklaringsbehovKode()
            }
        assertThat(spillAv().hendelser.map { it.hendelsesTidspunkt to it.tilstand.gjeldendeAvklaringsBehov })
            .containsExactlyElementsOf(
                gjenoppbygd.hendelser.map { it.hendelsesTidspunkt to it.tilstand.gjeldendeAvklaringsBehov },
            )
    }

    @Test
    fun `bygger historikk og bestiller resending av saksstatistikk`() {
        val hendelse =
            hendelseFraFil("avklaringsbehovhendelser/fullfort_forstegangsbehandling.json")
        val behandlingRepository = FakeBehandlingRepository()
        val sakRepository = FakeSakRepository()

        val hendelsePublisher = FakeHendelsePublisher()
        val historikkService = RebyggBehandlingshistorikkService(
            SakService(sakRepository),
            PersonService(FakePersonRepository(), FakePdlGateway()),
            behandlingRepository,
            BehandlingService(behandlingRepository, SkjermingService(FakePdlGateway(emptyMap()))),
            hendelsePublisher,
        )

        historikkService.rebyggHistorikk(hendelse)

        val behandling = behandlingRepository.hent(hendelse.behandlingReferanse)!!

        assertThat(behandling.behandlingStatus()).isEqualTo(BehandlingStatus.AVSLUTTET)
        assertThat(hendelsePublisher.hendelser).containsExactly(
            StatistikkHendelse.SakstatistikkSkalResendes(behandling.id()),
        )
    }
}
