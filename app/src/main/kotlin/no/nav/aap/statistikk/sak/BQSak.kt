package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.behandling.BehandlingStatus
import no.nav.aap.statistikk.behandling.SøknadsFormat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

data class BQBehandling(
    val fagsystemNavn: String = "Kelvin",
    val sekvensNummer: Long,
    val behandlingUUID: String,
    val relatertBehandlingUUID: String? = null,
    val relatertFagsystem: String? = null,
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
    val opprettetAv: String,
    val ansvarligBeslutter: String?,
    val vedtakTid: LocalDateTime? = null,
    val søknadsFormat: SøknadsFormat,
    val saksbehandler: String?,
    val behandlingMetode: BehandlingMetode,
    val behandlingStatus: String,
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
        require(vedtakTid == null || vedtakTid.truncatedTo(SECONDS).isEqual(vedtakTid))
    }
}

enum class BehandlingMetode {
    MANUELL, AUTOMATISK
}

/**
 * @param id Skal referere til sekvensnummer.
 */
data class BQVilkårsPrøving(val id: String, val beskrivelse: String, val resultat: String)