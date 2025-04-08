package no.nav.aap.statistikk.vilkårsresultat

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.behandlingsflyt.kontrakt.statistikk.Vilkårtype
import no.nav.aap.statistikk.behandling.TypeBehandling
import no.nav.aap.statistikk.sak.tilSaksnummer
import no.nav.aap.statistikk.testutils.opprettTestHendelse
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsPeriodeEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*
import javax.sql.DataSource

class VilkårsresultatRepositoryTest {
    @Test
    fun `fungerer å lagre vilkårs-resultat og hente inn igjen`(@Postgres dataSource: DataSource) {
        val randomUUID = UUID.randomUUID()
        val saksnummer = "ABCDE".tilSaksnummer()
        val (behandlingId, _) = opprettTestHendelse(dataSource, randomUUID, saksnummer)

        val vilkårsResultatEntity = VilkårsResultatEntity(
            id = null, listOf(
                VilkårEntity(
                    id = null,
                    Vilkårtype.MEDLEMSKAP.toString(), listOf(
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(5),
                            LocalDate.now(),
                            Utfall.IKKE_OPPFYLT.toString(),
                            false,
                            null,
                            null
                        ),
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(3),
                            LocalDate.now(),
                            Utfall.IKKE_OPPFYLT.toString(),
                            false,
                            null,
                            null
                        )
                    )
                ),
                VilkårEntity(
                    id = null,
                    Vilkårtype.SYKDOMSVILKÅRET.toString(), listOf(
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(5),
                            LocalDate.now(),
                            Utfall.IKKE_OPPFYLT.toString(),
                            false,
                            null,
                            null
                        ),
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(3),
                            LocalDate.now(),
                            Utfall.OPPFYLT.toString(),
                            true,
                            null,
                            null
                        )
                    )
                )
            )
        )

        val generertId = dataSource.transaction { conn ->
            val repo = VilkårsresultatRepository(conn)
            repo.lagreVilkårsResultat(vilkårsResultatEntity, behandlingId)
        }

        assertThat(generertId).isNotNull()

        val hentetUt = dataSource.transaction {
            VilkårsresultatRepository(it).hentVilkårsResultat(generertId)
        }
        assertThat(
            hentetUt.tilVilkårsResultat(
                saksnummer = saksnummer,
                behandlingsReferanse = randomUUID,
                typeBehandling = TypeBehandling.Førstegangsbehandling.toString()
            )
        ).isEqualTo(
            vilkårsResultatEntity.tilVilkårsResultat(
                saksnummer = saksnummer,
                behandlingsReferanse = randomUUID,
                typeBehandling = TypeBehandling.Førstegangsbehandling.toString()
            )
        )

        val hentUtMedReferanse =
            dataSource.transaction { VilkårsresultatRepository(it).hentForBehandling(randomUUID) }

        assertThat(hentUtMedReferanse).isEqualTo(hentetUt)
    }

    @Test
    fun `kun ett vilkårsresultat per behandling - skal få exception`(@Postgres dataSource: DataSource) {
        val randomUUID = UUID.randomUUID()
        val saksnummer = "saksnummer".tilSaksnummer()

        val (behandlingId, _) = opprettTestHendelse(dataSource, randomUUID, saksnummer)

        val vilkårsresultat = VilkårsResultatEntity(
            id = null, listOf(
                VilkårEntity(
                    id = null,
                    Vilkårtype.MEDLEMSKAP.toString(), listOf(
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(5),
                            LocalDate.now(),
                            "utfall",
                            false,
                            null,
                            null
                        ),
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(3),
                            LocalDate.now(),
                            "utfall2",
                            false,
                            null,
                            null
                        )
                    )
                ),
            )
        )

        // Lagre én gang
        dataSource.transaction { conn ->
            VilkårsresultatRepository(conn).lagreVilkårsResultat(vilkårsresultat, behandlingId)
        }

        // Lagre én gang til skal kaste avbrudd
        assertThrows<Exception> {
            dataSource.transaction {
                VilkårsresultatRepository(it).lagreVilkårsResultat(
                    vilkårsresultat, behandlingId
                )
            }
        }
    }
}