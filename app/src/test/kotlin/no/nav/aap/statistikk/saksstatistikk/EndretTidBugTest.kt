package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.testutils.builders.konstruerSakstatistikkService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Tester rundt kollisjoner i endretTid og ManglerEnhet-retry-flyten.
 *
 * Se docs/endretTid-bug-analyse.md for full analyse.
 */
class EndretTidBugTest {

    /**
     * Bekrefter rotårsak-fiksen for Feilkilde 1:
     * Når lagreSakInfoTilBigquery kalles på nytt etter at AVSLUTTET allerede er lagret,
     * returnerer mapper AVSLUTTET@T (duplikat av det som allerede er lagret) → skippes.
     *
     * Dette er kjernen i hvorfor "alltid kall lagreSakInfoTilBigquery på retry" er trygt:
     * fersk beregning produserer AVSLUTTET@T som er identisk med lagret verdi → duplikat → ingen ny rad.
     */
    @Test
    fun `lagreSakInfoTilBigquery på retry etter AVSLUTTET - duplikat oppdages og ingen ny rad lagres`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingUUID = UUID.randomUUID()
        val endretTid = LocalDateTime.of(2024, 6, 1, 12, 0, 0)

        dataSource.transaction { conn ->
            val service = konstruerSakstatistikkService(conn)
            val repo = SakstatistikkRepositoryImpl(conn)

            val avsluttetSnapshot = lagTestBQBehandling(
                behandlingUUID = behandlingUUID,
                endretTid = endretTid,
                registrertTid = endretTid.minusHours(2),
                behandlingStatus = "AVSLUTTET",
            )
            service.lagreBQBehandling(avsluttetSnapshot)

            val antallFørRetry = repo.hentAlleHendelserPåBehandling(behandlingUUID).size

            // Simuler retry: fersk beregning produserer samme AVSLUTTET@T (duplikat)
            service.lagreBQBehandling(avsluttetSnapshot)

            val alleRader = repo.hentAlleHendelserPåBehandling(behandlingUUID)
            assertThat(alleRader.size)
                .describedAs("Duplikat AVSLUTTET skal ikke gi ny rad — retry er trygg")
                .isEqualTo(antallFørRetry)
            assertThat(alleRader.maxBy { it.endretTid }.behandlingStatus).isEqualTo("AVSLUTTET")
        }
    }

    /**
     * Dokumenterer atferd ved direkte kall til lagreBQBehandling når IVERKSETTES
     * ankommer med samme endretTid som AVSLUTTET.
     * I dette tilfellet bumpes IVERKSETTES opp (T+1µs) og havner etter AVSLUTTET.
     */
    @Test
    fun `direkte lagreBQBehandling - IVERKSETTES bumpes til T+1µs etter AVSLUTTET i generell sti`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingUUID = UUID.randomUUID()
        val endretTid = LocalDateTime.of(2024, 6, 1, 12, 0, 0)

        dataSource.transaction { conn ->
            val service = konstruerSakstatistikkService(conn)
            val repo = SakstatistikkRepositoryImpl(conn)

            // Steg 1: AVSLUTTET lagres
            val avsluttetSnapshot = lagTestBQBehandling(
                behandlingUUID = behandlingUUID,
                endretTid = endretTid,
                registrertTid = endretTid.minusHours(2),
                behandlingStatus = "AVSLUTTET",
            )
            service.lagreBQBehandling(avsluttetSnapshot)

            // Steg 2: IVERKSETTES med samme endretTid via direkte lagreBQBehandling
            // (ikke via lagreMedStoredBQBehandling som nå korrigerer endretTid)
            val frossenIverksettes = lagTestBQBehandling(
                behandlingUUID = behandlingUUID,
                endretTid = endretTid,          // ← samme T som AVSLUTTET
                registrertTid = endretTid.minusHours(2),
                behandlingStatus = "IVERKSETTES",
            )
            service.lagreBQBehandling(frossenIverksettes)

            val alleRader = repo.hentAlleHendelserPåBehandling(behandlingUUID)

            // Den generelle stien i lagreBQBehandling bumper fortsatt opp til T+1µs.
            // Rotårsak-fiksen forhindrer at dette skjer via retry-flyten.
            val sisteRad = alleRader.maxBy { it.endretTid }
            assertThat(sisteRad.behandlingStatus).isEqualTo("IVERKSETTES")
            assertThat(sisteRad.endretTid).isEqualTo(endretTid.plusNanos(1000))
        }
    }

    /**
     * Samme som forrige test, men for UNDER_BEHANDLING.
     * Den generelle stien i lagreBQBehandling bumper opp til T+1µs — AVSLUTTET er ikke lenger sist.
     * Rotårsak-fiksen for Feilkilde 1 forhindrer at dette skjer via retry-flyten.
     */
    @Test
    fun `direkte lagreBQBehandling - UNDER_BEHANDLING bumpes til T+1µs etter AVSLUTTET i generell sti`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingUUID = UUID.randomUUID()
        val endretTid = LocalDateTime.of(2024, 6, 1, 12, 0, 0)

        dataSource.transaction { conn ->
            val service = konstruerSakstatistikkService(conn)
            val repo = SakstatistikkRepositoryImpl(conn)

            // Steg 1: AVSLUTTET lagres (registrertTid == endretTid → erInngangsHendelse=true → ingen OPPRETTET-rad)
            service.lagreBQBehandling(
                lagTestBQBehandling(
                    behandlingUUID = behandlingUUID,
                    endretTid = endretTid,
                    registrertTid = endretTid,
                    behandlingStatus = "AVSLUTTET",
                )
            )

            // Steg 2: UNDER_BEHANDLING med SAMME endretTid → bumpes til endretTid+1000ns (+1µs)
            service.lagreBQBehandling(
                lagTestBQBehandling(
                    behandlingUUID = behandlingUUID,
                    endretTid = endretTid,  // ← samme T → trigger bump
                    registrertTid = endretTid,
                    behandlingStatus = "UNDER_BEHANDLING",
                )
            )

            val alleRader = repo.hentAlleHendelserPåBehandling(behandlingUUID)
            val sisteRad = alleRader.maxBy { it.endretTid }

            // Den generelle stien bumper UNDER_BEHANDLING til T+1µs.
            // Rotårsak-fiksen forhindrer at dette skjer via retry-flyten.
            assertThat(sisteRad.behandlingStatus).isEqualTo("UNDER_BEHANDLING")
            assertThat(sisteRad.endretTid).isEqualTo(endretTid.plusNanos(1000))
        }
    }

    /**
     * Bekrefter Feilkilde 4: hentHendelseMedEndretTid filtrerer på er_relast,
     * slik at en vanlig hendelse og en resend-hendelse ved samme endretTid
     * ikke oppdager hverandre som duplikater, og begge kan lagres.
     */
    @Test
    fun `GJELDENDE OPPFØRSEL - vanlig og resend-hendelse ved samme endretTid lagres som to separate rader`(
        @Postgres dataSource: DataSource
    ) {
        val behandlingUUID = UUID.randomUUID()
        val t2 = LocalDateTime.of(2024, 6, 1, 12, 0, 0)
        val t1 = t2.minusMinutes(10)  // eldre enn siste → går inn i "historisk posisjon"-grenen

        dataSource.transaction { conn ->
            val service = konstruerSakstatistikkService(conn)
            val repo = SakstatistikkRepositoryImpl(conn)

            // Lagre nyeste rad (inngangshendelse = registrertTid == endretTid → ingen OPPRETTET-rad)
            service.lagreBQBehandling(
                lagTestBQBehandling(
                    behandlingUUID = behandlingUUID,
                    endretTid = t2,
                    registrertTid = t2,
                    behandlingStatus = "UNDER_BEHANDLING",
                )
            )

            // Vanlig hendelse ved eldre tidspunkt → lagres historisk med endretTid = t1
            service.lagreBQBehandling(
                lagTestBQBehandling(
                    behandlingUUID = behandlingUUID,
                    endretTid = t1,
                    registrertTid = t2,
                    behandlingStatus = "AVSLUTTET",
                    erResending = false,
                )
            )

            val antallEtterVanlig = repo.hentAlleHendelserPåBehandling(behandlingUUID).size

            // Resend-hendelse med nøyaktig samme innhold og endretTid = t1
            // Burde oppdages som duplikat, men hentHendelseMedEndretTid filtrerer på er_relast
            service.lagreBQBehandling(
                lagTestBQBehandling(
                    behandlingUUID = behandlingUUID,
                    endretTid = t1,
                    registrertTid = t2,
                    behandlingStatus = "AVSLUTTET",
                    erResending = true,   // ← eneste forskjell
                )
            )

            val alleRader = repo.hentAlleHendelserPåBehandling(behandlingUUID)

            // GJELDENDE OPPFØRSEL: resend-hendelsen lagres som ny rad selv om innholdet er identisk
            assertThat(alleRader.size)
                .describedAs(
                    "GJELDENDE OPPFØRSEL: resend-hendelse med samme endretTid og innhold " +
                            "oppdages ikke som duplikat av vanlig hendelse pga. er_relast-filter. " +
                            "Antall rader: ${alleRader.size}, forventet etter korrekt fiks: $antallEtterVanlig"
                )
                .isGreaterThan(antallEtterVanlig)
        }
    }
}

private fun lagTestBQBehandling(
    behandlingUUID: UUID = UUID.randomUUID(),
    endretTid: LocalDateTime = LocalDateTime.now(),
    registrertTid: LocalDateTime = endretTid,
    behandlingStatus: String = "UNDER_BEHANDLING",
    erResending: Boolean = false,
) = BQBehandling(
    behandlingUUID = behandlingUUID,
    behandlingType = "FØRSTEGANGSBEHANDLING",
    aktorId = "12345678901",
    saksnummer = "TESTSAKSNR",
    tekniskTid = LocalDateTime.now(),
    registrertTid = registrertTid,
    endretTid = endretTid,
    versjon = "v1",
    mottattTid = registrertTid,
    opprettetAv = "Kelvin",
    ansvarligBeslutter = null,
    søknadsFormat = no.nav.aap.statistikk.behandling.SøknadsFormat.DIGITAL,
    saksbehandler = null,
    behandlingMetode = BehandlingMetode.MANUELL,
    behandlingStatus = behandlingStatus,
    behandlingÅrsak = "SØKNAD",
    resultatBegrunnelse = null,
    ansvarligEnhetKode = "4491",
    sakYtelse = "AAP",
    erResending = erResending,
)
