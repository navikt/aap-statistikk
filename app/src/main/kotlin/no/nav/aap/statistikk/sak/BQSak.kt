package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.behandling.SøknadsFormat
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

private val logger = LoggerFactory.getLogger("no.nav.aap.statistikk.sak")

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
    val behandlingÅrsak: String,
    val ansvarligEnhetKode: String?,
    val sakYtelse: String,
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
        if (ansvarligEnhetKode == null) {
            logger.info("Fant ikke ansvarlig enhet for behandling $behandlingUUID med saksnummer $saksnummer.")
        }
        if (saksbehandler == null) {
            logger.info("Fant ikke saksbehandler for behandling $behandlingUUID med saksnummer $saksnummer.")
        }
    }
}

enum class BehandlingMetode {
    MANUELL, AUTOMATISK
}

/**
 * @param id Skal referere til sekvensnummer.
 */
data class BQVilkårsPrøving(val id: String, val beskrivelse: String, val resultat: String)