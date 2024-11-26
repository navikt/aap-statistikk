package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagTabell
import no.nav.aap.statistikk.sak.BQBehandling
import no.nav.aap.statistikk.sak.SakTabell
import no.nav.aap.statistikk.tilkjentytelse.BQTilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.vilkårsresultat.BQVilkårsResultatPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BQRepository::class.java)

class BQRepository(
    private val client: BigQueryClient
) : IBQRepository {
    private val vilkårsVurderingTabell = VilkårsVurderingTabell()
    private val tilkjentYtelseTabell = TilkjentYtelseTabell()
    private val beregningsGrunnlagTabell = BeregningsGrunnlagTabell()
    private val sakTabell = SakTabell()
    private val behandlingTabell = BehandlingTabell()

    override fun lagre(payload: Vilkårsresultat) {
        logger.info("Lagrer vilkårsresultat.")
        val flatetListe = payload.vilkår.flatMap { v ->
            v.perioder.map {
                BQVilkårsResultatPeriode(
                    saksnummer = payload.saksnummer,
                    behandlingsReferanse = payload.behandlingsReferanse,
                    behandlingsType = payload.behandlingsType,
                    vilkårtype = v.vilkårType,
                    fraDato = it.fraDato,
                    tilDato = it.tilDato,
                    utfall = it.utfall,
                    manuellVurdering = it.manuellVurdering
                )
            }
        }
        client.insertMany(vilkårsVurderingTabell, flatetListe)
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

    override fun lagre(payload: BeregningsGrunnlagBQ) {
        logger.info("Lagrer beregningsgrunnlag.")
        client.insert(
            beregningsGrunnlagTabell, payload
        )
    }

    override fun lagre(payload: BQYtelseBehandling) {
        logger.info("Lagrer BQYtelseBehandling for behandling ${payload.referanse}.")
        client.insert(behandlingTabell, payload)
    }

    override fun lagre(payload: BQBehandling) {
        logger.info("Lagrer saksinfo.")
        client.insert(sakTabell, payload)
    }

    override fun toString(): String {
        return "BQRepository(behandlingTabell=$behandlingTabell, client=$client, vilkårsVurderingTabell=$vilkårsVurderingTabell, tilkjentYtelseTabell=$tilkjentYtelseTabell, beregningsGrunnlagTabell=$beregningsGrunnlagTabell, sakTabell=$sakTabell)"
    }

}