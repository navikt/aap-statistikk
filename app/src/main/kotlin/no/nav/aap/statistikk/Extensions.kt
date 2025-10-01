package no.nav.aap.statistikk

import java.time.LocalDateTime


fun LocalDateTime.isBeforeOrEqual(other: LocalDateTime): Boolean =
    this.isBefore(other) || this == other

fun LocalDateTime.isAfterOrEqual(other: LocalDateTime): Boolean =
    this.isAfter(other) || this == other
