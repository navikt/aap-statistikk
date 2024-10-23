package no.nav.aap.statistikk.sak

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

data class BQBehandling(
    val sekvensNummer: Long,
    val behandlingUUID: String,
    val relatertBehandlingUUID: String? = null,
    val ferdigbehandletTid: LocalDateTime? = null,
    val behandlingType: String,
    val aktorId: String,
    val saksnummer: String,
    val tekniskTid: LocalDateTime,
    val registrertTid: LocalDateTime,
    val endretTid: LocalDateTime,
    val verson: String,
    val avsender: String,
    val mottattTid: LocalDateTime,
) {
    init {
        require(behandlingType.uppercase() == behandlingType)
        require(mottattTid.truncatedTo(SECONDS).isEqual(mottattTid))
        require(
            registrertTid.truncatedTo(SECONDS).isEqual(registrertTid)
        )
        require(
            ferdigbehandletTid == null || ferdigbehandletTid.truncatedTo(SECONDS)
                .isEqual(ferdigbehandletTid)
        )
    }
}