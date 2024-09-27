package no.nav.aap.statistikk

import io.micrometer.core.instrument.MeterRegistry

fun MeterRegistry.hendelseLagret() =
    this.counter(
        "statistikk_lagret_stoppet_hendelse_total",
    )

fun MeterRegistry.avsluttetBehandlingDtoLagret() =
    this.counter(
        "statistikk_lagret_avsluttet_behandling_dto_total",
    )

fun MeterRegistry.avsluttetBehandlingLagret() =
    this.counter(
        "statistikk_lagret_avsluttet_behandling_total",
    )