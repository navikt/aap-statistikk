package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BQYtelseBehandling
import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagBQ
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagTabell
import no.nav.aap.statistikk.saksstatistikk.SakTabell
import no.nav.aap.statistikk.tilkjentytelse.BQTilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelse
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.vilkårsresultat.BQVilkårsResultatPeriode
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(BQYtelseRepository::class.java)

class BQYtelseRepository(
    private val client: BigQueryClient
) : IBQYtelsesstatistikkRepository {
    private val vilkårsVurderingTabell = VilkårsVurderingTabell()
    private val tilkjentYtelseTabell = TilkjentYtelseTabell()
    private val beregningsGrunnlagTabell = BeregningsGrunnlagTabell()
    private val sakTabell = SakTabell()
    private val behandlingTabell = BehandlingTabell()

    private data class TableWithValues<E>(val table: BQTable<E>, val values: List<E>)

    private val valsToCommit = mutableListOf<TableWithValues<*>>()

    private fun <E> addToCommit(
        tabell: BQTable<E>,
        payload: List<E>
    ) {
        val existingEntry = valsToCommit.find { it.table == tabell }
        if (existingEntry != null) {
            @Suppress("UNCHECKED_CAST")
            val typedEntry = existingEntry as TableWithValues<E>
            valsToCommit.remove(existingEntry)
            valsToCommit.add(TableWithValues(tabell, typedEntry.values + payload))
        } else {
            valsToCommit.add(TableWithValues(tabell, payload))
        }
    }

    override fun lagre(payload: Vilkårsresultat) {
        logger.info("Lagrer vilkårsresultat.")
        val flatetListe = payload.vilkår.flatMap { v ->
            v.perioder.map {
                BQVilkårsResultatPeriode(
                    saksnummer = payload.saksnummer,
                    behandlingsReferanse = payload.behandlingsReferanse,
                    behandlingsType = payload.behandlingsType.toString(),
                    vilkårtype = v.vilkårType,
                    fraDato = it.fraDato,
                    tilDato = it.tilDato,
                    utfall = it.utfall.toString(),
                    manuellVurdering = it.manuellVurdering
                )
            }
        }
        addToCommit(vilkårsVurderingTabell, flatetListe)
    }

    override fun lagre(payload: TilkjentYtelse) {
        logger.info("Lagrer tilkjent ytelse.")

        val values = payload.perioder.map {
            BQTilkjentYtelse(
                saksnummer = payload.saksnummer,
                behandlingsreferanse = payload.behandlingsReferanse.toString(),
                fraDato = it.fraDato,
                tilDato = it.tilDato,
                dagsats = it.dagsats,
                gradering = it.gradering,
                antallBarn = it.antallBarn,
                barnetillegg = it.barnetillegg,
                barnetilleggSats = it.barnetilleggSats,
                redusertDagsats = it.redusertDagsats
            )
        }
        addToCommit(tilkjentYtelseTabell, values)
    }

    override fun lagre(payload: BeregningsGrunnlagBQ) {
        logger.info("Lagrer beregningsgrunnlag.")
        addToCommit(beregningsGrunnlagTabell, listOf(payload))
    }

    override fun lagre(payload: BQYtelseBehandling) {
        logger.info("Lagrer BQYtelseBehandling for behandling ${payload.referanse}.")
        client.insert(behandlingTabell, payload)
        addToCommit(behandlingTabell, listOf(payload))
    }

    override fun start() {
        valsToCommit.clear()
    }

    override fun commit() {
        valsToCommit.forEach { tableWithValues ->
            if (tableWithValues.values.isNotEmpty()) {
                logger.info("Lagrer ${tableWithValues.values.size} rader til tabell ${tableWithValues.table.tableName}.")
                insertValues(tableWithValues)
            } else {
                logger.info("Ingen rader å lagre til tabell ${tableWithValues.table.tableName}.")
            }
        }
    }

    private fun <E> insertValues(tableWithValues: TableWithValues<E>) {
        client.insertMany(tableWithValues.table, tableWithValues.values)
    }

    override fun toString(): String {
        return "BQRepository(behandlingTabell=$behandlingTabell, client=$client, vilkårsVurderingTabell=$vilkårsVurderingTabell, tilkjentYtelseTabell=$tilkjentYtelseTabell, beregningsGrunnlagTabell=$beregningsGrunnlagTabell, sakTabell=$sakTabell)"
    }
}
