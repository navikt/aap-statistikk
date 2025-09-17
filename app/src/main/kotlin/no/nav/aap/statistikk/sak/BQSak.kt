package no.nav.aap.statistikk.sak

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.SøknadsFormat
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

private val logger = LoggerFactory.getLogger("no.nav.aap.statistikk.sak")

/**
 * @param endretTid Også kalt "funksjonellTid". Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
 * @param tekniskTid Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.
 * @param mottattTid Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid. Denne verdien må være mindre eller lik registrertTid.
 * @param registrertTid Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
 */
data class BQBehandling(
    val fagsystemNavn: String = "Kelvin",
    val sekvensNummer: Long,
    val behandlingUUID: String,
    val relatertBehandlingUUID: String? = null,
    val relatertFagsystem: String? = null,
    private val ferdigbehandletTid: LocalDateTime? = null,
    val behandlingType: String,
    val aktorId: String,
    val saksnummer: String,
    val tekniskTid: LocalDateTime,
    val registrertTid: LocalDateTime,
    val endretTid: LocalDateTime,
    val verson: String,
    val avsender: String = KELVIN,
    val mottattTid: LocalDateTime,
    val opprettetAv: String,
    val ansvarligBeslutter: String?,
    val vedtakTid: LocalDateTime? = null,
    val søknadsFormat: SøknadsFormat,
    val saksbehandler: String?,
    val behandlingMetode: BehandlingMetode,
    val behandlingStatus: String,
    val behandlingÅrsak: String,
    val behandlingResultat: String? = null,
    val resultatBegrunnelse: String?,
    val ansvarligEnhetKode: String?,
    val sakYtelse: String,
) {
    init {
        require(behandlingType.uppercase() == behandlingType)
        require(mottattTid.truncatedTo(SECONDS).isEqual(mottattTid))

        require(
            registrertTid.truncatedTo(SECONDS).isEqual(registrertTid)
        )
        require(mottattTid.isBefore(registrertTid) || mottattTid.isEqual(registrertTid)) { "Mottatt tid $mottattTid må være mindre eller lik registrert tid $registrertTid." }
        require(vedtakTid == null || vedtakTid.truncatedTo(SECONDS).isEqual(vedtakTid))
        if (ansvarligEnhetKode == null) {
            logger.info("Fant ikke ansvarlig enhet for behandling $behandlingUUID med saksnummer $saksnummer.")
        }
        if (saksbehandler == null) {
            logger.info("Fant ikke saksbehandler for behandling $behandlingUUID med saksnummer $saksnummer.")
        }
    }

    val ferdigBehandletTidTrunkert = ferdigbehandletTid?.truncatedTo(SECONDS)
}

enum class BehandlingMetode {
    MANUELL, AUTOMATISK, KVALITETSSIKRING, FATTE_VEDTAK
}