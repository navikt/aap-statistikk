package no.nav.aap.statistikk

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.nav.aap.statistikk.behandling.TypeBehandling

fun MeterRegistry.hendelseLagret(): Counter = this.counter(
    "statistikk_lagret_stoppet_hendelse_total",
)

fun MeterRegistry.avsluttetBehandlingLagret(): Counter = this.counter(
    "statistikk_lagret_avsluttet_behandling_total",
)

fun MeterRegistry.nyBehandlingOpprettet(type: TypeBehandling): Counter =
    this.counter("statistikk_ny_behandling_opprettet_total", listOf(Tag.of("type", type.name)))

fun MeterRegistry.oppgaveHendelseMottatt(): Counter =
    this.counter("statistikk_oppgave_hendelse_mottatt_total")

fun MeterRegistry.lagretPostmottakHendelse(): Counter =
    this.counter("statistikk_lagret_postmottak_hendelse_total")

fun MeterRegistry.årsakTilOpprettelseIkkeSatt(): Counter =
    this.counter("statistikk_aarsak_til_opprettelse_ikke_satt_total")