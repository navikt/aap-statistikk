package no.nav.aap.statistikk

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.avsluttetbehandling.ResultatKode
import no.nav.aap.statistikk.avsluttetbehandling.RettighetsType
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.kodeverk.Kodeverk
import no.nav.aap.statistikk.kodeverk.KodeverkRepository
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.vilkårsresultat.Vilkårtype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class KodeverksTest {
    @Test
    fun `alle vilkårstyper eksisterer i kodeverkstabell`(
        @Postgres dataSource: DataSource,
    ) {
        val vilkår = hentVilkårTyper(dataSource) {
            this.hentVilkår()
        }

        assertThat(vilkår).containsExactlyInAnyOrder(*Vilkårtype.entries.map { it.name }
            .toTypedArray())
    }

    @Test
    fun `alle resultatkoder eksisterer i kodeverkstabell`(
        @Postgres dataSource: DataSource,
    ) {
        val resultatkoder = hentVilkårTyper(dataSource) {
            this.hentResultat()
        }

        assertThat(resultatkoder).containsExactlyInAnyOrder(*ResultatKode.entries.map { it.name }
            .toTypedArray())
    }

    @Test
    fun `alle rettighetstyper eksisterer i kodeverkstabell`(
        @Postgres dataSource: DataSource,
    ) {
        val rettighetstyper = hentVilkårTyper(dataSource) {
            this.hentRettighetstype()
        }

        // Oppdater når det kommer flere
        assertThat(rettighetstyper).containsExactlyInAnyOrder(
            "BISTANDSBEHOV",
            "SYKEPENGEERSTATNING",
            "STUDENT"
        )

    }

    @Test
    fun `alle behandlingstyper eksisterer i kodeverkstabell`(
        @Postgres dataSource: DataSource,
    ) {
        val behandlingstyper = hentVilkårTyper(dataSource) {
            this.hentBehandlingType()
        }

        // Oppdater når det kommer flere
        assertThat(behandlingstyper).containsExactlyInAnyOrder(
            "Førstegangsbehandling",
            "Revurdering",
            "Klage",
            "SvarFraAndreinstans"
        )
    }


    private fun hentVilkårTyper(
        dataSource: DataSource,
        koder: KodeverkRepository.() -> List<Kodeverk>
    ): List<String> {
        val vilkår = dataSource.transaction { connection ->
            KodeverkRepository(connection).koder().map { it.kode }
        }
        return vilkår
    }
}