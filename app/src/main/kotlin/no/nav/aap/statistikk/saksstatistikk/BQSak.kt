package no.nav.aap.statistikk.saksstatistikk

import no.nav.aap.statistikk.KELVIN
import no.nav.aap.statistikk.behandling.SøknadsFormat
import no.nav.aap.statistikk.isBeforeOrEqual
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.UUID

/**
 * @param endretTid Også kalt "funksjonellTid". Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
 * @param tekniskTid Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.
 * @param mottattTid Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid. Denne verdien må være mindre eller lik registrertTid.
 * @param registrertTid Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
 */
data class BQBehandling(
    val fagsystemNavn: String = "Kelvin",
    val sekvensNummer: Long?,
    val behandlingUUID: UUID,
    val relatertBehandlingUUID: String? = null,
    val relatertFagsystem: String? = null,
    val ferdigbehandletTid: LocalDateTime? = null,
    val behandlingType: String,
    val aktorId: String,
    val saksnummer: String,
    val tekniskTid: LocalDateTime,
    val registrertTid: LocalDateTime,
    val endretTid: LocalDateTime,
    val versjon: String,
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
    val erResending: Boolean
) : Comparable<BQBehandling> {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(behandlingType.uppercase() == behandlingType)

        // Bug i SAF i mars.
        if (mottattTid.isAfter(LocalDate.of(2025, 4, 1).atStartOfDay())) {
            if (!mottattTid.isBeforeOrEqual(registrertTid)) {
                logger.warn("Mottatt tid $mottattTid må være mindre eller lik registrert tid $registrertTid. Saksnr: $saksnummer. BehandlingUUID: $behandlingUUID")
            }
        }
        if (ansvarligEnhetKode == null) {
            logger.info("Fant ikke ansvarlig enhet for behandling $behandlingUUID med saksnummer $saksnummer.")
        }
        if (saksbehandler == null) {
            logger.info("Fant ikke saksbehandler for behandling $behandlingUUID med saksnummer $saksnummer.")
        }
    }

    val vedtakTidTrunkert = vedtakTid?.truncatedTo(SECONDS)

    fun ansesSomDuplikat(other: BQBehandling): Boolean {
        return this == other.copy(
            sekvensNummer = sekvensNummer,
            erResending = erResending,
            tekniskTid = tekniskTid,
            endretTid = endretTid,
            versjon = versjon
        )
    }

    override fun compareTo(other: BQBehandling): Int {
        return if (endretTid == other.endretTid) tekniskTid.compareTo(other.tekniskTid) else endretTid.compareTo(
            other.endretTid
        )
    }
}

enum class BehandlingMetode {
    MANUELL, AUTOMATISK, KVALITETSSIKRING, FATTE_VEDTAK
}