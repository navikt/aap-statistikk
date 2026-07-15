package no.nav.aap.statistikk

import io.micrometer.core.instrument.MeterRegistry

interface WithMetrics {
    fun registrerMetrics(registry: MeterRegistry)
}
