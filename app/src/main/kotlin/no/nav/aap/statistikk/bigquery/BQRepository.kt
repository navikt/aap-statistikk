package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.avsluttetbehandling.IBeregningsGrunnlag
import no.nav.aap.statistikk.avsluttetbehandling.MedBehandlingsreferanse
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagTabell
import no.nav.aap.statistikk.tilkjentytelse.BQTilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
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
        client.insert(vilkårsVurderingTabell, payload)
    }

    override fun lagre(payload: TilkjentYtelse) {
        logger.info("Lagrer tilkjent ytelse.")

        client.insertMany(tilkjentYtelseTabell, payload.perioder.map {
            BQTilkjentYtelse(
                saksnummer = payload.saksnummer,
                behandlingsreferanse = payload.behandlingsReferanse.toString(),
                fraDato = it.fraDato,
                tilDato = it.tilDato,
                dagsats = it.dagsats,
                gradering = it.gradering
            )
        })
    }

    override fun lagre(payload: IBeregningsGrunnlag, behandlingsReferanse: UUID) {
        logger.info("Lagrer beregningsgrunnlag.")
        client.insert(
            beregningsGrunnlagTabell,
            MedBehandlingsreferanse(value = payload, behandlingsReferanse = behandlingsReferanse)
        )
    }

    override fun toString(): String {
        return "BQRepository(client=$client, vilkårsVurderingTabell=$vilkårsVurderingTabell)"
    }
}