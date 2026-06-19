package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.Collections.synchronizedList
import java.util.concurrent.CountDownLatch
import java.util.function.BiPredicate
import javax.sql.DataSource

class SakstatistikkRepositoryImplTest {

    private val datoSammenligner: BiPredicate<LocalDateTime, LocalDateTime> = BiPredicate { t, u ->
        t.truncatedTo(ChronoUnit.MILLIS).equals(u.truncatedTo(ChronoUnit.MILLIS))
    }

    private fun opprettTestHendelse(
        referanse: UUID,
        tekniskTid: LocalDateTime,
        registrertTid: LocalDateTime,
        mottattTid: LocalDateTime,
        endretTid: LocalDateTime,
        relatertBehandlingUUID: String = UUID.randomUUID().toString()
    ) = BQBehandling(
        fagsystemNavn = "KELVIN",
        sekvensNummer = 1,
        behandlingUUID = referanse,
        relatertBehandlingUUID = relatertBehandlingUUID,
        relatertFagsystem = "Kelvin",
        ferdigbehandletTid = LocalDateTime.now(),
        behandlingType = "REVURDERING",
        aktorId = "123456",
        saksnummer = "123",
        tekniskTid = tekniskTid,
        registrertTid = registrertTid,
        endretTid = endretTid,
        versjon = "versjon",
        avsender = KELVIN,
        mottattTid = mottattTid,
        opprettetAv = KELVIN,
        ansvarligBeslutter = "Z1234",
        vedtakTid = LocalDateTime.now().minusMinutes(20),
        søknadsFormat = SøknadsFormat.DIGITAL,
        saksbehandler = "1234",
        behandlingMetode = BehandlingMetode.MANUELL,
        behandlingStatus = "UNDER_BEHANDLING",
        behandlingÅrsak = "SØKNAD",
        ansvarligEnhetKode = "1337",
        sakYtelse = "AAP",
        behandlingResultat = "AX",
        resultatBegrunnelse = "BEGRUNNELSE",
        erResending = true,
    )

    @Test
    fun `lagre og hente ut igjen`(@Postgres dataSource: DataSource) {
        val referanse = UUID.randomUUID()
        val tekniskTid = LocalDateTime.now()
        val registrertTid = LocalDateTime.now().minusMinutes(10)
        val mottattTid = LocalDateTime.now().minusMinutes(20)
        val endretTid = LocalDateTime.now().plusSeconds(1)

        val hendelse =
            opprettTestHendelse(referanse, tekniskTid, registrertTid, mottattTid, endretTid)

        dataSource.transaction {
            val repository = SakstatistikkRepositoryImpl(it)
            repository.lagre(hendelse)
        }

        val uthentet = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentAlleHendelserPåBehandling(referanse)
        }

        assertThat(uthentet).hasSize(1)

        assertThat(uthentet.first())
            .usingRecursiveComparison()
            .withEqualsForType(datoSammenligner, LocalDateTime::class.java)
            .isEqualTo(hendelse)
    }

    @Test
    fun `hent siste for behandling sorterer både tekniskTid og endretTid`(@Postgres dataSource: DataSource) {
        val referanse = UUID.randomUUID()
        val nå = LocalDateTime.of(2025, 4, 1, 12, 0)
        val tekniskTid = nå
        val registrertTid = nå.minusMinutes(10)
        val mottattTid = nå.minusMinutes(20)
        val endretTid = nå.plusSeconds(1)

        val hendelse =
            opprettTestHendelse(referanse, tekniskTid, registrertTid, mottattTid, endretTid)

        dataSource.transaction {
            val repository = SakstatistikkRepositoryImpl(it)
            repository.lagre(
                hendelse.copy(
                    endretTid = endretTid.minusHours(1),
                    tekniskTid = tekniskTid.minusHours(1),
                    behandlingStatus = "OPPRETTET"
                )
            )
            repository.lagre(hendelse)
            repository.lagre(hendelse.copy(tekniskTid = tekniskTid.plusHours(1)))
        }

        val res = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentSisteHendelseForBehandling(referanse)
        }

        assertThat(res).usingRecursiveComparison()
            .withEqualsForType(datoSammenligner, LocalDateTime::class.java)
            .ignoringFields("sekvensNummer")
            .isEqualTo(
                hendelse.copy(tekniskTid = tekniskTid.plusHours(1))
            )
    }

    @Test
    fun `acquireBehandlingLock oppretter lås-rad og er idempotent`(@Postgres dataSource: DataSource) {
        val uuid = UUID.randomUUID()

        dataSource.transaction { conn ->
            val repository = SakstatistikkRepositoryImpl(conn)
            repository.acquireBehandlingLock(uuid)
        }

        // Kall igjen i ny transaksjon — skal ikke kaste
        dataSource.transaction { conn ->
            val repository = SakstatistikkRepositoryImpl(conn)
            repository.acquireBehandlingLock(uuid)
        }
    }

    @Test
    fun `acquireBehandlingLock fungerer for flere ulike behandlinger samtidig`(@Postgres dataSource: DataSource) {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()

        dataSource.transaction { conn ->
            val repository = SakstatistikkRepositoryImpl(conn)
            repository.acquireBehandlingLock(uuid1)
            repository.acquireBehandlingLock(uuid2)
        }
    }

    @Test
    fun `acquireBehandlingLock serialiserer samtidige skriv til samme behandling`(@Postgres dataSource: DataSource) {
        val uuid = UUID.randomUUID()
        val skriveRekkefølge = synchronizedList(mutableListOf<Int>())

        // Tråd 1 signaliserer her når låsen er tatt
        val låsErtatt = CountDownLatch(1)
        // Tråd 1 venter her til testen gir klarsignal til å committe
        val klartilCommit = CountDownLatch(1)

        val feil1 = arrayOfNulls<Throwable>(1)
        val feil2 = arrayOfNulls<Throwable>(1)

        val tråd1 = Thread {
            runCatching {
                dataSource.transaction { conn ->
                    SakstatistikkRepositoryImpl(conn).acquireBehandlingLock(uuid)
                    låsErtatt.countDown()          // Tråd 2 kan nå prøve å ta låsen
                    klartilCommit.await()          // Vent til tråd 2 er blokkert
                    Thread.sleep(100)              // Gi tråd 2 tid til å blokkere på SELECT FOR UPDATE
                    skriveRekkefølge.add(1)
                }                                  // Commit — låsen slippes
            }.onFailure { feil1[0] = it }
        }

        val tråd2 = Thread {
            runCatching {
                låsErtatt.await()                  // Vent til tråd 1 har låsen
                klartilCommit.countDown()          // Gi tråd 1 klarsignal til å sove og committe
                dataSource.transaction { conn ->
                    SakstatistikkRepositoryImpl(conn).acquireBehandlingLock(uuid) // Blokkerer til tråd 1 committer
                    skriveRekkefølge.add(2)
                }
            }.onFailure { feil2[0] = it }
        }

        tråd1.start()
        tråd2.start()
        tråd1.join(5_000)
        tråd2.join(5_000)

        assertThat(feil1[0]).describedAs("Tråd 1 kastet exception").isNull()
        assertThat(feil2[0]).describedAs("Tråd 2 kastet exception").isNull()
        assertThat(skriveRekkefølge)
            .describedAs("Tråd 1 skal alltid skrive før tråd 2 slipper inn")
            .containsExactly(1, 2)
    }
}