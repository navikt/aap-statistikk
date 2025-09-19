package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.testutils.Postgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class SakstatistikkRepositoryImplTest {
    @Test
    fun `lagre og hente ut igjen`(@Postgres dataSource: DataSource) {
        val referanse = UUID.randomUUID()
        val tekniskTid = LocalDateTime.now()
        val registrertTid = LocalDateTime.now().minusMinutes(10)
        val mottattTid = LocalDateTime.now().minusMinutes(20)
        val endretTid = LocalDateTime.now().plusSeconds(1)

        val relatertBehandlingUUID = UUID.randomUUID()
        val hendelse = BQBehandling(
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
            resultatBegrunnelse = "BEGRUNNELSE"
        )
        val id = dataSource.transaction {
            val repository = SakstatistikkRepositoryImpl(it)
            repository.lagre(hendelse)
        }

        val uthentet = dataSource.transaction {
            SakstatistikkRepositoryImpl(it).hentSisteForBehandling(id)
        }

        assertThat(uthentet)
            .usingRecursiveComparison()
            .isEqualTo(hendelse)
    }
}