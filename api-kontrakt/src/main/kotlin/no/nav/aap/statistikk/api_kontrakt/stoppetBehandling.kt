package no.nav.aap.statistikk.api_kontrakt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * @param saksnummer Saksnummer.
 * @param behandlingReferanse Behandlingsreferanse
 * @param mottattTid Dato for første søknad mottatt for behandlingen.
 * @param status Behandlingstatus. Ikke det samme som sakstatus.
 */
public data class StoppetBehandling(
    val saksnummer: String,
    val sakStatus: SakStatus,
    val behandlingReferanse: UUID,
    val behandlingOpprettetTidspunkt: LocalDateTime,
    val mottattTid: LocalDateTime,
    val status: BehandlingStatus,
    val behandlingType: TypeBehandling,
    val ident: String,
    val versjon: String,
    val avklaringsbehov: List<AvklaringsbehovHendelse>,
    val hendelsesTidspunkt: LocalDateTime,
    val avsluttetBehandling: AvsluttetBehandlingDTO? = null,
) {
    init {
        require(ident.isNotEmpty())
        require(status == BehandlingStatus.AVSLUTTET || avsluttetBehandling == null)
        require(status != BehandlingStatus.AVSLUTTET || avsluttetBehandling != null)
    }
}

public enum class SakStatus {
    OPPRETTET,
    UTREDES,
    LØPENDE,
    AVSLUTTET
}

public enum class BehandlingStatus {
    OPPRETTET,
    UTREDES,
    IVERKSETTES,
    AVSLUTTET;
}


public enum class TypeBehandling {
    Førstegangsbehandling,
    Revurdering,
    Tilbakekreving,
    Klage;
}


public data class AvklaringsbehovHendelse(
    val definisjon: Definisjon,
    val status: EndringStatus,
    val endringer: List<Endring>
)

public data class Definisjon(
    val type: String, // TODO: enum her
    val behovType: BehovType,
    val løsesISteg: String
)


public enum class EndringStatus {
    OPPRETTET,
    AVSLUTTET,
    TOTRINNS_VURDERT,
    SENDT_TILBAKE_FRA_BESLUTTER,
    KVALITETSSIKRET,
    SENDT_TILBAKE_FRA_KVALITETSSIKRER,
    AVBRUTT
}

public data class Endring(
    val status: EndringStatus,
    val tidsstempel: LocalDateTime,
    val frist: LocalDate? = null,
    val endretAv: String
)

public enum class BehovType {
    MANUELT_PÅKREVD,
    MANUELT_FRIVILLIG,
    VENTEPUNKT,
}