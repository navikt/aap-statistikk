package no.nav.aap.statistikk.hendelser.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.annotations.type.`object`.example.ExampleProvider
import com.papsign.ktor.openapigen.annotations.type.`object`.example.WithExample
import no.nav.aap.statistikk.api_kontrakt.eksempelUUID
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

enum class Status {
    OPPRETTET,
    UTREDES,
    AVSLUTTET;
}

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


@Request("Denne sendes ved hvert stopp i en behandling. Brukes for å populere basen med ID-er.")
@WithExample
data class MottaStatistikkDTO(
    @JsonProperty(value = "saksnummer", required = true) val saksnummer: String,
    val behandlingReferanse: UUID,
    val behandlingOpprettetTidspunkt: LocalDateTime,
    @JsonProperty(value = "status", required = true) val status: String,
    @JsonProperty(value = "behandlingType", required = true) val behandlingType: TypeBehandling,
    val ident: String,
    val avklaringsbehov: List<AvklaringsbehovHendelse>
) {
    companion object : ExampleProvider<MottaStatistikkDTO> {
        override val example: MottaStatistikkDTO = MottaStatistikkDTO(
            saksnummer = "4LFL5CW",
            behandlingReferanse = eksempelUUID,
            status = "OPPRETTET",
            behandlingType = TypeBehandling.Førstegangsbehandling,
            ident = "1403199012345",
            behandlingOpprettetTidspunkt = LocalDateTime.now(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = 5001,
                        behovType = BehovType.MANUELT_PÅKREVD,
                        løsesISteg = "AVKLAR_STUDENT"

                    ),
                    status = EndringStatus.AVSLUTTET,
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.OPPRETTET,
                            tidsstempel = LocalDateTime.now().minusMinutes(10),
                            endretAv = "Kelvin"
                        ),
                        Endring(
                            status = EndringStatus.AVSLUTTET,
                            tidsstempel = LocalDateTime.now().minusMinutes(5),
                            endretAv = "Z994573"
                        )
                    )
                ),
                AvklaringsbehovHendelse(
                    definisjon = Definisjon(
                        type = 5003,
                        behovType = BehovType.MANUELT_PÅKREVD,
                        løsesISteg = "AVKLAR_SYKDOM"
                    ),
                    status = EndringStatus.OPPRETTET,
                    endringer = listOf(
                        Endring(
                            status = EndringStatus.OPPRETTET,
                            tidsstempel = LocalDateTime.now().minusMinutes(3),
                            endretAv = "Kelvin"
                        )
                    )
                )
            )
        )
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
    val type: Int,
    val behovType: BehovType,
    val løsesISteg: String
)

enum class BehovType {
    MANUELT_PÅKREVD,
    MANUELT_FRIVILLIG,
    VENTEPUNKT,
}