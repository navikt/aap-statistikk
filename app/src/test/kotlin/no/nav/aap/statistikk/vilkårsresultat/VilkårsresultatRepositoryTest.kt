package no.nav.aap.statistikk.vilkårsresultat

import no.nav.aap.statistikk.WithPostgresContainer
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsPeriodeEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

class VilkårsresultatRepositoryTest : WithPostgresContainer() {
    @Test
    fun `fungerer å lagre vilkårs-resultat og hente inn igjen`() {
        val dataSource = dataSource()

        val repo = VilkårsresultatRepository(dataSource)

        val vilkårsResultatEntity = VilkårsResultatEntity(
            id = null, behandlingsReferanse = UUID.randomUUID().toString(),
            "saksnummer", "typeBehandling", listOf(
                VilkårEntity(
                    id = null,
                    "vilkårType", listOf(
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
                            LocalDate.now().minusDays(3), LocalDate.now(), "utfall2", false, null, null
                        )
                    )
                ),
                VilkårEntity(
                    id = null,
                    "vilkårType2", listOf(
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
                            LocalDate.now().minusDays(3), LocalDate.now(), "utfa4ll2", true, null, null
                        )
                    )
                )
            )
        )

        val generertId = repo.lagreVilkårsResultat(vilkårsResultatEntity)

        assertThat(generertId).isNotNull()

        val hentetUt = repo.hentVilkårsResultat(generertId!!)

        assertThat(hentetUt!!.tilVilkårsResultat()).isEqualTo(vilkårsResultatEntity.tilVilkårsResultat())
    }
}