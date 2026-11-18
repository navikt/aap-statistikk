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
        .map {
            if (nyeQueue.isEmpty()) {
                val last = sorterteNye.last()
                forrigeNye = last
                last.copy(endretTid = it.endretTid)
            } else if (nærNokITid(it.endretTid, nyeQueue.peek().endretTid)) {
                val ny = nyeQueue.poll()
                forrigeNye = ny
                ny.copy(endretTid = it.endretTid)
            } else if (it.endretTid.isBefore(nyeQueue.peek().endretTid) && forrigeNye != null) {
                /*
                GAMMEL: -------   X ---------
                NY    : -- Z ------- Y ------ (Z = forrigeNye)
                 */
                forrigeNye.copy(endretTid = it.endretTid)
            } else if (it.endretTid.isBefore(nyeQueue.peek().endretTid) && forrigeNye == null) {
                /*
                GAMMEL: ---- X ---------
                NY    : ---------- Y ---
                 */
                val ny = nyeQueue.poll()
                forrigeNye = ny
                ny.copy(endretTid = it.endretTid)
            } else if (it.endretTid.isAfter(nyeQueue.peek().endretTid) && forrigeNye != null) {
                /*
                GAMMEL: ------------ X --
                NY    : -- Z --- Y ------
                 */
                val ny = nyeQueue.poll()
                forrigeNye = ny
                ny.copy(endretTid = it.endretTid)
            } else if (it.endretTid.isAfter(nyeQueue.peek().endretTid) && forrigeNye == null) {
                /*
                GAMMEL: ------------ X --
                NY    : ------ Y ------
                 */
                val ny = nyeQueue.poll()
                forrigeNye = ny
                ny.copy(endretTid = it.endretTid)
            } else {
                error("Ukjent situasjon")
            }
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
