package no.nav.aap.statistikk

import java.time.LocalDateTime


fun LocalDateTime.isBeforeOrEqual(other: LocalDateTime): Boolean =
    this.isBefore(other) || this == other

