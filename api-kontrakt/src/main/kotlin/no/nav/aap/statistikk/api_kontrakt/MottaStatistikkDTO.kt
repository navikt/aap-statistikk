package no.nav.aap.statistikk.api_kontrakt

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * @param saksnummer Saksnummer.
 */
data class MottaStatistikkDTO(
    val saksnummer: String,
    val behandlingReferanse: UUID,
    val behandlingOpprettetTidspunkt: LocalDateTime,
    val status: String,
    val behandlingType: TypeBehandling,
    val ident: String,
    val avklaringsbehov: List<AvklaringsbehovHendelse>
)

enum class TypeBehandling(private var identifikator: String) {
    Førstegangsbehandling("ae0034"),
    Revurdering("ae0028"),
    Tilbakekreving(""),
    Klage("");

    companion object {
        fun from(identifikator: String): TypeBehandling {
            return entries.first { it.identifikator == identifikator }
        }
    }
}


data class AvklaringsbehovHendelse(
    val definisjon: Definisjon,
    val status: EndringStatus,
    val endringer: List<Endring>
)


enum class EndringStatus {
    OPPRETTET,
    AVSLUTTET,
    TOTRINNS_VURDERT,
    SENDT_TILBAKE_FRA_BESLUTTER,
    KVALITETSSIKRET,
    SENDT_TILBAKE_FRA_KVALITETSSIKRER,
    AVBRUTT
}

data class Endring(
    val status: EndringStatus,
    val tidsstempel: LocalDateTime,
    val frist: LocalDate? = null,
    val endretAv: String
)


data class Definisjon(
    val type: String,
    val behovType: BehovType,
    val løsesISteg: String
)

enum class BehovType {
    MANUELT_PÅKREVD,
    MANUELT_FRIVILLIG,
    VENTEPUNKT,
}