package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.beregningsgrunnlag.repository.BeregningsGrunnlagTabell
import no.nav.aap.statistikk.saksstatistikk.SakTabell
import no.nav.aap.statistikk.tilkjentytelse.TilkjentYtelseTabell
import no.nav.aap.statistikk.vilkårsresultat.VilkårsVurderingTabell


typealias SchemaRegistry = Map<String, BQTable<*>>

val schemaRegistryYtelseStatistikk: SchemaRegistry = mapOf(
    BeregningsGrunnlagTabell.TABLE_NAME to BeregningsGrunnlagTabell(),
    VilkårsVurderingTabell.TABLE_NAME to VilkårsVurderingTabell(),
    TilkjentYtelseTabell.TABLE_NAME to TilkjentYtelseTabell(),
    BehandlingTabell.TABLE_NAME to BehandlingTabell(),
)

val schemaRegistrySakStatistikk: SchemaRegistry = mapOf(
    SakTabell.TABLE_NAME to SakTabell(),
)