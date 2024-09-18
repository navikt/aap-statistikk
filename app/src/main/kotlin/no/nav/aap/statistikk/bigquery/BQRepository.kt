package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger(BQRepository::class.java)

class BQRepository(
    private val client: BigQueryClient
) : IBQRepository {
    private val vilkårsVurderingTabell = VilkårsVurderingTabell()
    private val tilkjentYtelseTabell = TilkjentYtelseTabell()
    private val beregningsGrunnlagTabell = BeregningsGrunnlagTabell()

    override fun lagre(payload: Vilkårsresultat) {
        logger.info("Lagrer vilkårsresultat.")
        client.create(vilkårsVurderingTabell)
        client.insert(vilkårsVurderingTabell, payload)
    }

    override fun lagre(payload: TilkjentYtelse) {
        logger.info("Lagrer tilkjent ytelse.")
        client.create(tilkjentYtelseTabell)
        client.insert(tilkjentYtelseTabell, payload)
    }

    override fun lagre(payload: IBeregningsGrunnlag, behandlingsReferanse: UUID) {
        logger.info("Lagrer beregningsgrunnlag.")
        client.create(beregningsGrunnlagTabell)
        client.insert(
            beregningsGrunnlagTabell,
            MedBehandlingsreferanse(value = payload, behandlingsReferanse = behandlingsReferanse)
        )
    }

    override fun toString(): String {
        return "BQRepository(client=$client, vilkårsVurderingTabell=$vilkårsVurderingTabell)"
    }
}