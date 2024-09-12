package no.nav.aap.statistikk.vilkårsresultat

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.statistikk.testutils.Postgres
import no.nav.aap.statistikk.api_kontrakt.TypeBehandling
import no.nav.aap.statistikk.api_kontrakt.Vilkårtype
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
        opprettTestHendelse(dataSource, randomUUID, "ABCDE")

        val vilkårsResultatEntity = VilkårsResultatEntity(
            id = null, behandlingsReferanse = randomUUID.toString(),
            "ABCDE", TypeBehandling.Førstegangsbehandling.toString(), listOf(
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
                VilkårEntity(
                    id = null,
                    Vilkårtype.SYKDOMSVILKÅRET.toString(), listOf(
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(5),
                            LocalDate.now(),
                            "utf3all",
                            false,
                            null,
                            null
                        ),
                        VilkårsPeriodeEntity(
                            id = null,
                            LocalDate.now().minusDays(3),
                            LocalDate.now(),
                            "utfa4ll2",
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
            repo.lagreVilkårsResultat(vilkårsResultatEntity)
        }

        assertThat(generertId).isNotNull()

        val hentetUt = dataSource.transaction {
            VilkårsresultatRepository(it).hentVilkårsResultat(generertId!!)
        }
        assertThat(hentetUt!!.tilVilkårsResultat()).isEqualTo(vilkårsResultatEntity.tilVilkårsResultat())
    }

    @Test
    fun `kun ett vilkårsresultat per behandling - skal få exception`(@Postgres dataSource: DataSource) {
        val randomUUID = UUID.randomUUID()
        val saksnummer = "saksnummer"

        opprettTestHendelse(dataSource, randomUUID, saksnummer)

        // lagre
        val behandlingsReferanse = randomUUID.toString()

        val vilkårsresultat = VilkårsResultatEntity(
            id = null, behandlingsReferanse = behandlingsReferanse,
            saksnummer, "typeBehandling", listOf(
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
            VilkårsresultatRepository(conn).lagreVilkårsResultat(vilkårsresultat)
        }

        // Lagre én gang til skal kaste avbrudd
        assertThrows<Exception> {
            dataSource.transaction {
                VilkårsresultatRepository(it).lagreVilkårsResultat(
                    vilkårsresultat
                )
            }
        }
    }
}