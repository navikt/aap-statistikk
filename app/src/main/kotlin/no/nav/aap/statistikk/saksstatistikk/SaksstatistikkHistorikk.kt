package no.nav.aap.statistikk.saksstatistikk

import java.time.Duration
import java.time.LocalDateTime
import java.util.LinkedList

internal fun flettSaksstatistikkHistorikk(
    originaleHendelser: List<BQBehandling>,
    nyeHendelser: List<BQBehandling>,
): List<BQBehandling> {
    val sorterteNye = nyeHendelser.sortedBy { it.endretTid }
    val nyeQueue = LinkedList(sorterteNye)

    var forrigeNye: BQBehandling? = null
    val originaleMedNyData = originaleHendelser
        .sorted()
        .map { original ->
            val neste = nyeQueue.peek()
            val forrige = forrigeNye
            val valgt = when {
                neste == null -> sorterteNye.last()

                !nærNokITid(original.endretTid, neste.endretTid) &&
                    original.endretTid.isBefore(neste.endretTid) &&
                    forrige != null -> forrige

                else -> nyeQueue.poll()
            }
            forrigeNye = valgt
            valgt.copy(endretTid = original.endretTid)
        }

    // Gamle tidspunkter bevares før påfølgende duplikater fjernes.
    check(
        originaleMedNyData.map { it.endretTid }
            .toSet() == originaleHendelser.map { it.endretTid }.toSet(),
    )

    return (nyeQueue.toList() + originaleMedNyData).sorted()
        .fold(listOf()) { acc, curr ->
            val forrige = acc.lastOrNull()
            if (forrige == null) {
                acc + curr
            } else {
                if (forrige.ansesSomDuplikat(curr)) {
                    acc
                } else {
                    acc + curr
                }
            }
        }
}

internal fun nærNokITid(originalTid: LocalDateTime, nyTid: LocalDateTime): Boolean {
    val duration = Duration.between(originalTid, nyTid)
    return duration.abs().toMillis() <= 10
}
