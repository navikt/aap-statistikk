package no.nav.aap.statistikk.bigquery

import no.nav.aap.statistikk.behandling.BehandlingTabell


typealias SchemaRegistry = Map<String, BQTable<*>>

val schemaRegistryYtelseStatistikk: SchemaRegistry = mapOf(
    BehandlingTabell.TABLE_NAME to BehandlingTabell(),
)