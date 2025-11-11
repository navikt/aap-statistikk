package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BehandlingTabell
import no.nav.aap.statistikk.saksstatistikk.SakTabell


typealias SchemaRegistry = Map<String, BQTable<*>>

val schemaRegistryYtelseStatistikk: SchemaRegistry = mapOf(
    BehandlingTabell.TABLE_NAME to BehandlingTabell(),
)

val schemaRegistrySakStatistikk: SchemaRegistry = mapOf(
    SakTabell.TABLE_NAME to SakTabell(),
)