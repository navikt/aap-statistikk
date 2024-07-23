package no.nav.aap.statistikk.vilkårsresultat.service

import no.nav.aap.statistikk.bigquery.BQRepository
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(VilkårsResultatService::class.java)

class VilkårsResultatService(
    dataSource: DataSource,
    private val bqRepository: BQRepository
) {
    private val vilkårsResultatRepository = VilkårsresultatRepository(dataSource)

    fun mottaVilkårsResultat(vilkårsresultat: Vilkårsresultat): Int {
        val id = vilkårsResultatRepository.lagreVilkårsResultat(
            VilkårsResultatEntity.fraDomene(vilkårsresultat)
        )

        logger.info("Lagrer vilkårsresultat i BigQuery. $vilkårsresultat")
        bqRepository.lagre(vilkårsresultat)
        return id
    }
}