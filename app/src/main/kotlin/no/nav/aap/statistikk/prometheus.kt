package no.nav.aap.statistikk

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.nav.aap.statistikk.behandling.TypeBehandling

fun MeterRegistry.hendelseLagret() = this.counter(
    "statistikk_lagret_stoppet_hendelse_total",
)

fun MeterRegistry.avsluttetBehandlingLagret() = this.counter(
    "statistikk_lagret_avsluttet_behandling_total",
)

fun MeterRegistry.nyBehandlingOpprettet(type: TypeBehandling) =
    this.counter("statistikk_ny_behandling_opprettet_total", listOf(Tag.of("type", type.name)))

fun MeterRegistry.oppgaveHendelseMottatt() =
    this.counter("statistikk_oppgave_hendelse_mottatt_total")