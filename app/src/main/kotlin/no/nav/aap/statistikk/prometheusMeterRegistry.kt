package no.nav.aap.statistikk

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry


class PrometheusProvider {
    companion object {
        var prometheus: MeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }
}