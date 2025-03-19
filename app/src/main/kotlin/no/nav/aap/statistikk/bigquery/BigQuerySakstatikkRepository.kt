package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagTabell
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.SakTabell
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell

private val logger = org.slf4j.LoggerFactory.getLogger(BigQuerySakstatikkRepository::class.java)

class BigQuerySakstatikkRepository
    (
    private val client: BigQueryClient
) : IBQSakstatistikkRepository {
    private val vilkårsVurderingTabell = VilkårsVurderingTabell()
    private val tilkjentYtelseTabell = TilkjentYtelseTabell()
    private val beregningsGrunnlagTabell = BeregningsGrunnlagTabell()
    private val sakTabell = SakTabell()
    private val behandlingTabell = BehandlingTabell()


    override fun lagre(payload: BQBehandling) {
        logger.info("Lagrer saksinfo.")
        client.insert(sakTabell, payload)
    }

    override fun toString(): String {
        return "BQRepository(behandlingTabell=$behandlingTabell, client=$client, vilkårsVurderingTabell=$vilkårsVurderingTabell, tilkjentYtelseTabell=$tilkjentYtelseTabell, beregningsGrunnlagTabell=$beregningsGrunnlagTabell, sakTabell=$sakTabell)"
    }
}