package no.nav.aap.statistikk.saksstatistikk

import io.mockk.mockk
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.StoppetBehandling
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vurderingsbehov
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.BehandlingRepository
import no.nav.aap.statistikk.hendelser.BehandlingService
import no.nav.aap.statistikk.hendelser.HendelsesService
import no.nav.aap.statistikk.meldekort.MeldekortRepository
import no.nav.aap.statistikk.oppgave.HendelseType
import no.nav.aap.statistikk.oppgave.OppgaveHendelse
import no.nav.aap.statistikk.oppgave.OppgaveHendelseRepositoryImpl
import no.nav.aap.statistikk.oppgave.Oppgavestatus
import no.nav.aap.statistikk.person.PersonRepository
import no.nav.aap.statistikk.person.PersonService
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakService
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.konstruerSakstatistikkService
import no.nav.aap.verdityper.dokument.Kanal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BehandlingStatus
import no.nav.aap.behandlingsflyt.kontrakt.sak.Status as SakStatus

class SaksStatistikkServiceTest {
    @Test
    fun `hente ut historikk`(@Postgres dataSource: DataSource) {

        val referanse = lagreHendelser(dataSource)
        val behandlingId =
            dataSource.transaction { BehandlingRepository(it).hent(referanse)!!.id!! }

        val alleHendelser = dataSource.transaction {
            val sakStatikkService = konstruerSakstatistikkService(it)

            sakStatikkService.alleHendelserPåBehandling(behandlingId)
        }

        dataSource.transaction {
            konstruerSakstatistikkService(
                it
            ).lagreSakInfoTilBigquery(behandlingId)
        }

        assertThat(alleHendelser.last().ansvarligEnhetKode).isEqualTo("0220")
        assertThat(alleHendelser.map { it.endretTid }).doesNotHaveDuplicates()
        assertThat(alleHendelser.size).isEqualTo(2)
    }

    companion object {

        fun lagreHendelser(dataSource: DataSource): UUID {
            val behandlingReferanse = UUID.randomUUID()
            val mottattTid = LocalDateTime.now()
            dataSource.transaction {
                val hendelsesService = HendelsesService(
                    sakService = SakService(SakRepositoryImpl(it)),
                    // mockk fordi irrelevant for denne testen
                    avsluttetBehandlingService = mockk(relaxed = true),
                    personService = PersonService(PersonRepository(it)),
                    meldekortRepository = MeldekortRepository(it),
                    opprettBigQueryLagringSakStatistikkCallback = {},
                    behandlingService = BehandlingService(BehandlingRepository(it)),
                )

                OppgaveHendelseRepositoryImpl(it).lagreHendelse(
                    OppgaveHendelse(
                        hendelse = HendelseType.OPPRETTET,
                        oppgaveId = 1L,
                        mottattTidspunkt = LocalDateTime.now(),
                        personIdent = "12345678910",
                        saksnummer = "123456789",
                        behandlingRef = behandlingReferanse,
                        enhet = "0220",
                        avklaringsbehovKode = Definisjon.AVKLAR_SYKDOM.kode.name,
                        status = Oppgavestatus.OPPRETTET,
                        reservertAv = "123456789",
                        reservertTidspunkt = LocalDateTime.now(),
                        opprettetTidspunkt = LocalDateTime.now(),
                        endretAv = "123456789",
                        endretTidspunkt = LocalDateTime.now()
                    )
                )

                hendelsesService.prosesserNyHendelse(
                    StoppetBehandling(
                        saksnummer = "AAAA",
                        sakStatus = SakStatus.UTREDES,
                        behandlingReferanse = behandlingReferanse,
                        relatertBehandling = null,
                        behandlingOpprettetTidspunkt = LocalDateTime.now(),
                        mottattTid = mottattTid,
                        behandlingStatus = BehandlingStatus.OPPRETTET,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        soknadsFormat = Kanal.DIGITAL,
                        ident = "1233456",
                        versjon = "1",
                        vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                        avklaringsbehov = listOf(
                            AvklaringsbehovHendelseDto(
                                avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                                status = AvklaringsbehovStatus.OPPRETTET,
                                endringer = listOf(
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.OPPRETTET,
                                        tidsstempel = LocalDateTime.now(),
                                        endretAv = "Bork",
                                    )
                                ),
                            )
                        ),
                        hendelsesTidspunkt = LocalDateTime.now(),
                        avsluttetBehandling = null,
                        identerForSak = listOf("1233455", "11212")
                    )
                )

                hendelsesService.prosesserNyHendelse(
                    StoppetBehandling(
                        saksnummer = "AAAA",
                        sakStatus = SakStatus.UTREDES,
                        behandlingReferanse = behandlingReferanse,
                        relatertBehandling = null,
                        behandlingOpprettetTidspunkt = LocalDateTime.now(),
                        mottattTid = mottattTid,
                        behandlingStatus = BehandlingStatus.OPPRETTET,
                        behandlingType = TypeBehandling.Førstegangsbehandling,
                        soknadsFormat = Kanal.DIGITAL,
                        ident = "1233456",
                        versjon = "1",
                        vurderingsbehov = listOf(Vurderingsbehov.VURDER_RETTIGHETSPERIODE),
                        avklaringsbehov = listOf(
                            AvklaringsbehovHendelseDto(
                                avklaringsbehovDefinisjon = Definisjon.VURDER_RETTIGHETSPERIODE,
                                status = AvklaringsbehovStatus.AVSLUTTET,
                                endringer = listOf(
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.OPPRETTET,
                                        tidsstempel = LocalDateTime.now().minusSeconds(1),
                                        endretAv = "Bork",
                                    ),
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.AVSLUTTET,
                                        tidsstempel = LocalDateTime.now(),
                                        endretAv = "Bork",
                                    )
                                ),
                            ),
                            AvklaringsbehovHendelseDto(
                                avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                                status = AvklaringsbehovStatus.OPPRETTET,
                                endringer = listOf(
                                    EndringDTO(
                                        status = AvklaringsbehovStatus.OPPRETTET,
                                        tidsstempel = LocalDateTime.now(),
                                        endretAv = "Bjork",
                                    )
                                ),
                            ),
                        ),
                        hendelsesTidspunkt = LocalDateTime.now(),
                        avsluttetBehandling = null,
                        identerForSak = listOf("1233455", "11212")
                    )
                )
            }

            return behandlingReferanse
        }
    }
}