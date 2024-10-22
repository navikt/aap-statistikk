package no.nav.aap.statistikk.sak

import java.time.LocalDateTime

data class BQBehandling(
    val sekvensNummer: Long,
    val behandlingUUID: String,
    val relatertBehandlingUUID: String? = null,
    val behandlingType: String,
    val aktorId: String,
    val saksnummer: String,
    val tekniskTid: LocalDateTime,
    val registrertTid: LocalDateTime,
    val verson: String,
    val avsender: String,
    val mottattTid: LocalDateTime,
) {
    init {
        require(behandlingType.uppercase() == behandlingType)
        require(mottattTid.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).isEqual(mottattTid))
        require(
            registrertTid.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).isEqual(registrertTid)
        )
    }
}