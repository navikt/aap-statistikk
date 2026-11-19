package no.nav.aap.statistikk.behandling

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.oppgave.Saksbehandler
import no.nav.aap.statistikk.person.Ident
import no.nav.aap.statistikk.sak.Sak
import no.nav.aap.statistikk.sak.SakRepositoryImpl
import no.nav.aap.statistikk.sak.SakStatus
import no.nav.aap.statistikk.sak.Saksnummer
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.opprettTestPerson
import no.nav.aap.utbetaling.helved.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class BehandlingTilstandsvalgRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC)
    private val tidspunkt = LocalDateTime.now(clock)

    @Test
    fun `lagring kombinerer ytre verdier med historikkens status og største tidspunkt`(
        @Postgres dataSource: DataSource,
    ) {
        val siste = hendelse().copy(
            hendelsesTidspunkt = tidspunkt.minusMinutes(1),
            status = BehandlingStatus.AVSLUTTET,
            versjon = Versjon("historikk"),
            mottattTid = tidspunkt.minusHours(1),
            søknadsformat = SøknadsFormat.DIGITAL,
            ansvarligBeslutter = "Historikkbeslutter",
            vedtakstidspunkt = tidspunkt.minusMinutes(2),
            saksbehandler = Saksbehandler("Historikksaksbehandler"),
            resultat = ResultatKode.INNVILGET,
        )
        val behandling = behandling(dataSource).copy(
            status = BehandlingStatus.IVERKSETTES,
            oppdatertTidspunkt = tidspunkt.plusMinutes(1),
            ansvarligBeslutter = "Ytre beslutter",
            vedtakstidspunkt = tidspunkt.minusHours(2),
            sisteSaksbehandler = "Ytre saksbehandler",
            resultat = ResultatKode.AVSLAG,
            hendelser = listOf(
                siste.copy(hendelsesTidspunkt = tidspunkt, status = BehandlingStatus.UTREDES),
                siste,
            ),
        )
        val id = dataSource.transaction { BehandlingRepository(it, clock).opprettBehandling(behandling) }
        val forventet = behandling.copy(
            id = id,
            status = BehandlingStatus.AVSLUTTET,
            oppdatertTidspunkt = tidspunkt,
            hendelser = listOf(
                hendelse().copy(
                    status = BehandlingStatus.AVSLUTTET,
                    ansvarligBeslutter = "Ytre beslutter",
                    vedtakstidspunkt = tidspunkt.minusHours(2),
                    saksbehandler = Saksbehandler("Ytre saksbehandler"),
                    resultat = ResultatKode.AVSLAG,
                ),
            ),
        )

        assertThat(dataSource.transaction { BehandlingRepository(it).hent(id) })
            .usingRecursiveComparison().ignoringFields("snapShotId").isEqualTo(forventet)
        val lagretUtbetalingsid = dataSource.transaction {
            it.queryFirst("SELECT utbetaling_id FROM behandling_historikk WHERE behandling_id = ?") {
                setParams { setLong(1, id.id) }
                setRowMapper { row -> row.getString("utbetaling_id") }
            }
        }
        assertThat(lagretUtbetalingsid).isEqualTo(behandling.referanse.toBase64())
    }

    @Test
    fun `gjeldende rad normaliserer blanke verdier mens historikken beholder dem`(@Postgres dataSource: DataSource) {
        val behandling = behandling(dataSource).copy(
            ansvarligBeslutter = " ",
            sisteSaksbehandler = " ",
            venteÅrsak = " ",
            returÅrsak = " ",
            relatertBehandlingReferanse = " ",
            resultat = ResultatKode.INNVILGET,
        )
        val id = dataSource.transaction { BehandlingRepository(it, clock).opprettBehandling(behandling) }
        val forventet = behandling.copy(
            id = id,
            ansvarligBeslutter = null,
            sisteSaksbehandler = null,
            venteÅrsak = null,
            returÅrsak = null,
            relatertBehandlingReferanse = null,
        )
        val forventetHendelse = hendelse().copy(
            ansvarligBeslutter = " ",
            saksbehandler = Saksbehandler(" "),
            venteÅrsak = " ",
            returÅrsak = " ",
            relatertBehandlingReferanse = " ",
            resultat = ResultatKode.INNVILGET,
        )

        val gjeldendeOgHistorikk = dataSource.transaction {
            val repository = BehandlingRepository(it)
            listOf(repository.hentEllerNull(id), repository.hent(id))
        }

        assertThat(gjeldendeOgHistorikk).usingRecursiveComparison().ignoringFields("snapShotId")
            .isEqualTo(listOf(forventet, forventet.copy(hendelser = listOf(forventetHendelse))))
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 1])
    fun `like hendelsestidspunkter avgjøres av lagringstid og deretter id`(
        lagringsforskyvning: Long,
        @Postgres dataSource: DataSource,
    ) {
        val første = behandling(dataSource)
        val id = dataSource.transaction { BehandlingRepository(it, clock).opprettBehandling(første) }
        val andre = første.copy(id = id, status = BehandlingStatus.UTREDES, versjon = Versjon("andre"))
        val andreClock = Clock.offset(clock, Duration.ofMinutes(lagringsforskyvning))
        dataSource.transaction { BehandlingRepository(it, andreClock).oppdaterBehandling(andre) }
        val førsteHendelse = hendelse()
        val andreHendelse = hendelse().copy(
            tidspunkt = tidspunkt.plusMinutes(lagringsforskyvning),
            status = BehandlingStatus.UTREDES,
            versjon = Versjon("andre"),
        )
        val forventet = if (lagringsforskyvning < 0) {
            første.copy(id = id, hendelser = listOf(andreHendelse, førsteHendelse))
        } else {
            andre.copy(hendelser = listOf(førsteHendelse, andreHendelse))
        }

        assertThat(dataSource.transaction { BehandlingRepository(it).hent(id) })
            .usingRecursiveComparison().ignoringFields("snapShotId").isEqualTo(forventet)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `rekonstruksjon beholder tilstandsrekkefølgen men lagrer kumulativt største tidspunkt`(
        tidsforskyvning: Long,
        @Postgres dataSource: DataSource,
    ) {
        val behandling = behandling(dataSource)
        val id = dataSource.transaction { BehandlingRepository(it, clock).opprettBehandling(behandling) }
        val første = hendelse().copy(status = BehandlingStatus.UTREDES, versjon = Versjon("første"))
        val siste = første.copy(
            hendelsesTidspunkt = tidspunkt.plusMinutes(tidsforskyvning),
            status = BehandlingStatus.AVSLUTTET,
            versjon = Versjon("siste"),
        )
        val rekonstruksjonsClock = Clock.offset(clock, Duration.ofDays(1))
        dataSource.transaction {
            BehandlingRepository(it, rekonstruksjonsClock)
                .invaliderOgLagreNyHistorikk(behandling.copy(id = id, hendelser = listOf(første, siste)))
        }
        val forventetHendelse = hendelse().copy(
            tidspunkt = tidspunkt.plusDays(1),
            status = BehandlingStatus.UTREDES,
        )
        val forventet = behandling.copy(
            id = id,
            status = BehandlingStatus.AVSLUTTET,
            hendelser = listOf(
                forventetHendelse,
                forventetHendelse.copy(status = BehandlingStatus.AVSLUTTET),
            ),
        )

        assertThat(dataSource.transaction { BehandlingRepository(it).hent(id) })
            .usingRecursiveComparison().ignoringFields("snapShotId").isEqualTo(forventet)
    }

    private fun behandling(dataSource: DataSource): Behandling {
        val person = opprettTestPerson(dataSource, Ident("0"))
        val sak = Sak(
            saksnummer = Saksnummer("SAK123"),
            person = person,
            sakStatus = SakStatus.UTREDES,
            sistOppdatert = tidspunkt,
        )
        val lagretSak = dataSource.transaction {
            val repository = SakRepositoryImpl(it)
            repository.hentSak(repository.settInnSak(sak))
        }
        return Behandling(
            referanse = UUID.randomUUID(),
            sak = lagretSak,
            typeBehandling = TypeBehandling.Førstegangsbehandling,
            status = BehandlingStatus.OPPRETTET,
            opprettetTid = tidspunkt,
            mottattTid = tidspunkt.minusDays(1),
            versjon = Versjon("ytre"),
            søknadsformat = SøknadsFormat.PAPIR,
            oppdatertTidspunkt = tidspunkt,
        )
    }

    private fun hendelse(): BehandlingHendelse = BehandlingHendelse(
        tidspunkt = tidspunkt,
        hendelsesTidspunkt = tidspunkt,
        avklaringsbehovStatus = null,
        versjon = Versjon("ytre"),
        status = BehandlingStatus.OPPRETTET,
        mottattTid = tidspunkt.minusDays(1),
        søknadsformat = SøknadsFormat.PAPIR,
        relatertBehandlingReferanse = null,
    )
}
