package no.nav.aap.statistikk.sak

import java.time.LocalDateTime

data class BQBehandling(
    val sekvensNummer: Long,
    val behandlingUUID: String,
    val behandlingType: String,
    val saksnummer: String,
    val tekniskTid: LocalDateTime,
    val verson: String,
    val avsender: String,
) {
    init {
        require(behandlingType.uppercase() == behandlingType)
    }
}